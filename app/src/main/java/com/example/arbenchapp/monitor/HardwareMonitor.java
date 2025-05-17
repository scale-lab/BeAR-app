package com.example.arbenchapp.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Debug;
import android.util.Log;

import com.example.arbenchapp.datatypes.preprocessing.Settings;
import com.example.arbenchapp.util.ConversionUtil;
import com.example.arbenchapp.util.ImageConversionUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import androidx.preference.PreferenceManager;

import org.pytorch.Module;
import org.pytorch.IValue;
import org.pytorch.Tensor;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxTensorLike;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class HardwareMonitor {
    private static final String TAG = "HardwareMonitor";
    private final Context context;
    private static SharedPreferences prefs = null;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private long lastCpuTime = 0;
    private final long lastAppCpuTime = 0;

    public HardwareMonitor(Context context) {
        this.context = context;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static class BatteryStats {
        final double batteryLevel;
        final double current;      // in microamperes
        final double voltage;      // in millivolts
        final double temperature;  // in tenths of a degree Celsius
        final int status;

        BatteryStats(BatteryManager bm, Intent batteryIntent) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            this.batteryLevel = level * 100.0 / scale;

            // Get instantaneous current drain (negative indicates discharge)
            this.current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

            // Get voltage
            this.voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            // Get temperature
            this.temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0;

            // Get charging status
            this.status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        }
    }

    public static class HardwareMetrics {
        public double executionTimeMs;
        public double executionWithProcessing;
        public double fps;
        public double totalTime;
        public int totalFrames;
        public double cpuUsagePercent;
        public double cpuUsageDelta;
        public double threadCpuTimeMs;
        public long memoryUsedBytes;
        public long batteryPercentageUsed;
        public double powerConsumedMicroWattHours;
        public double temperatureChangeCelsius;
        public double finalTemperatureCelsius;
        public double averageCurrentDrainMicroAmps;
        public Map<String, Bitmap> output;

        public HardwareMetrics(
                double executionTimeMs,
                double executionWithProcessing,
                double fps,
                double totalTime,
                int totalFrames,
                double cpuUsagePercent,
                double cpuUsageDelta,
                double threadCpuTimeMs,
                long memoryUsedBytes,
                BatteryStats startStats,
                BatteryStats endStats) {
            this.executionTimeMs = executionTimeMs;
            this.executionWithProcessing = executionWithProcessing;
            this.fps = fps;
            this.totalTime = totalTime;
            this.totalFrames = totalFrames;
            this.cpuUsagePercent = cpuUsagePercent;
            this.cpuUsageDelta = cpuUsageDelta;
            this.threadCpuTimeMs = threadCpuTimeMs;
            this.memoryUsedBytes = memoryUsedBytes;
            this.batteryPercentageUsed = (long) (startStats.batteryLevel - endStats.batteryLevel);

            // Calculate power consumed using average current and voltage
            double avgVoltage = (startStats.voltage + endStats.voltage) / 2.0;
            this.averageCurrentDrainMicroAmps = Math.abs(endStats.current);
            double timeHours = executionTimeMs / (1e6 * 3.6); // Convert milliseconds to hours
            this.powerConsumedMicroWattHours =
                    Math.abs(averageCurrentDrainMicroAmps * avgVoltage * timeHours / 1000.0);

            this.temperatureChangeCelsius =
                    endStats.temperature - startStats.temperature;
            this.finalTemperatureCelsius = endStats.temperature;
        }

        public HardwareMetrics(Context context) {
            this.executionTimeMs = 0;
            this.executionWithProcessing = 0;
            this.fps = 0;
            this.totalTime = 0;
            this.totalFrames = 0;
            this.cpuUsagePercent = 0;
            this.cpuUsageDelta = 0;
            this.threadCpuTimeMs = 0;
            this.memoryUsedBytes = 0;

            Intent batteryIntent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            assert batteryIntent != null;
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            BatteryStats bs = new BatteryStats(bm, batteryIntent);

            this.batteryPercentageUsed = (long) (bs.batteryLevel);

            this.averageCurrentDrainMicroAmps = 0;
            this.powerConsumedMicroWattHours = 0;

            this.temperatureChangeCelsius = 0;
            this.finalTemperatureCelsius = bs.temperature;
        }
    }

    public HardwareMetrics getBaseMetrics() {
        double cpuUsage = getCpuUsage();
        return new HardwareMetrics(
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                cpuUsage,
                0.0,
                (double) getThreadCpuTimeMs(),
                (long) getAvailMemory(),
                getBatteryStats(),
                getBatteryStats());
    }

    private long lastRealTime = 0;
    private long lastCpuUsage = 0;
    private double cachedCpuUsage = 0;
    private double getCpuUsage() {
        try {
            long currentCpuTime = Debug.threadCpuTimeNanos();
            long currentRealTime = System.nanoTime();

            if (lastRealTime == 0) {
                lastCpuTime = currentCpuTime;
                lastRealTime = currentRealTime;
                return cachedCpuUsage;
            }

            long cpuTimeDelta = currentCpuTime - lastCpuTime;
            long realTimeDelta = currentRealTime - lastRealTime;

            lastCpuTime = currentCpuTime;
            lastRealTime = currentRealTime;

            if (realTimeDelta <= 0) {
                return lastCpuUsage;
            }

            double cpuUsage = ((double) cpuTimeDelta / realTimeDelta) * 100.0;
            int processors = Runtime.getRuntime().availableProcessors();
            cpuUsage = cpuUsage / processors;
            cachedCpuUsage = cpuUsage;

            return cpuUsage;
        } catch (Exception e) {
            Log.e(TAG, "Error reading CPU usage", e);
            return 0.0;
        }
    }

    private double getAvailMemory() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        return memInfo.availMem;
    }

    private BatteryStats getBatteryStats() {
        Intent batteryIntent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        assert batteryIntent != null;
        return new BatteryStats(batteryManager, batteryIntent);
    }

    private long getThreadCpuTimeMs() {
        return Debug.threadCpuTimeNanos() / 1_000_000; // Convert to milliseconds
    }

    public static class SplitModelMonitor {
        private final HardwareMonitor hardwareMonitor;
        private final ProcessingResultListener listener;

        private final BlockingQueue<OrtSession.Result> decoderQueue = new LinkedBlockingQueue<>(1);
        private final ExecutorService encoderService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setPriority(Thread.MAX_PRIORITY); // High priority for encoder
            return t;
        });
        private final ExecutorService decoderService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        private final OrtSession encoder;
        private final OrtSession[] decoders;
        private final OrtEnvironment env;
        private final Settings settings;
        private final Map<String, String> outputMappings;
        private final String inputName;

        private final Object dataLock = new Object();
        private final BlockingQueue<Double> frameTimes = new LinkedBlockingQueue<>(4);

        private boolean started;
        private HardwareMetrics startMetrics;
        private BatteryStats startBattery;
        private double startTime;
        private int numFrames;
        private double avgTime;
        private double avgTimePP;

        public SplitModelMonitor(
                OrtSession encoder,
                OrtSession[] decoders,
                OrtEnvironment env,
                Settings settings,
                String inputName,
                Context context) {
            this.hardwareMonitor = new HardwareMonitor(context);
            this.inputName = inputName;
            this.listener = null;
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }
            if (decoders.length == 0) {
                Log.e("SPLIT ERROR", "NO DECODERS SELECTED!");
            }

            this.encoder = encoder;
            this.decoders = decoders;
            this.env = env;
            this.settings = settings;
            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            this.outputMappings = gson.fromJson(json, type);

            this.started = false;
        }

        public SplitModelMonitor(
                OrtSession encoder,
                OrtSession[] decoders,
                OrtEnvironment env,
                Settings settings,
                String inputName,
                ProcessingResultListener listener,
                Context context) {
            this.hardwareMonitor = new HardwareMonitor(context);
            this.inputName = inputName;
            this.listener = listener;
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }
            if (decoders.length == 0) {
                Log.e("SPLIT ERROR", "NO DECODERS SELECTED!");
            }

            this.encoder = encoder;
            this.decoders = decoders;
            this.env = env;
            this.settings = settings;
            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            this.outputMappings = gson.fromJson(json, type);

            this.started = false;
        }

        public void startExecuteAndMonitor() {
            synchronized (dataLock) {
                started = true;
                startMetrics = hardwareMonitor.getBaseMetrics();
                startBattery = hardwareMonitor.getBatteryStats();
                startTime = System.nanoTime();
                numFrames = 0;
                avgTime = 0;
                avgTimePP = 0;
            }
        }

        public void queueRun(Bitmap bitmap) throws OrtException {
            if (listener == null) {
                System.err.println("QUEUE RUN ERROR: Listener cannot be null.");
                return;
            }
            // schedule encoder
            encoderService.submit(() -> {
                try {
                    System.out.println("uyoabdsfilun: Start encoder.");
                    double startTime = System.nanoTime();
                    OrtSession.Result result = runEncoder(bitmap);
                    double endTime = System.nanoTime();
                    frameTimes.put(startTime);
                    frameTimes.put(endTime);
                    decoderQueue.put(result);
                    System.out.println("uyoabdsfilun: Encoder time: " + ((endTime - startTime) / 1_000_000));
                    listener.requestNextFrame();
                } catch (InterruptedException e) {
                    System.err.println("QUEUE RUN ERROR: Interrupt while taking from encoder queue.");
                    listener.requestNextFrame();
                } catch (OrtException e) {
                    System.err.println("QUEUE RUN ERROR: Error running encoder session:: " + e);
                    listener.requestNextFrame();
                }
            });
            // schedule decoder
            decoderService.submit(() -> {
                try {
                    System.out.println("uyoabdsfilun: Trying to take from decoder queue.");
                    OrtSession.Result featureMap = decoderQueue.take();
                    System.out.println("uyoabdsfilun: Successfully took from decoder queue.");
                    double startDecoderTime = System.nanoTime();
                    Map<String, Bitmap> output = runDecoders(featureMap);
                    System.out.println("uyoabdsfilun: Decoder runtime = " + ((System.nanoTime() - startDecoderTime) / 1_000_000));
                    synchronized (dataLock) {
                        numFrames++;
                        double endTime = System.nanoTime();
                        double startFrameTime = frameTimes.take();
                        double endInferenceFrameTime = frameTimes.take();

                        if (numFrames == 1) {
                            avgTime = endInferenceFrameTime - startFrameTime;
                            avgTimePP = endTime - startFrameTime;
                        } else {
                            avgTime = ((avgTime * (numFrames - 1)) / numFrames) + ((endInferenceFrameTime - startFrameTime) / numFrames);
                            avgTimePP = ((avgTimePP * (numFrames - 1)) / numFrames) + ((endTime - startFrameTime) / numFrames);
                        }
                    }
                    listener.onProcessingComplete(bitmap, output);
                } catch (InterruptedException e) {
                    System.err.println("QUEUE RUN ERROR: Interrupt while taking from decoder queue.");
                } catch (OrtException e) {
                    System.err.println("QUEUE RUN ERROR: Error running decoder sessions:: " + e);
                }
            });
        }

        public Map<String, Bitmap> run(Bitmap input) throws OrtException {
            if (!started) {
                System.out.println("CANNOT RUN INFERENCE WITHOUT STARTING METRICS");
                return null;
            }
            double startInfTime;
            double endInfTime;
            double startPPTime = System.nanoTime();
            numFrames++;

            startInfTime = System.nanoTime();
            OrtSession.Result featureMap = runEncoder(input);
            endInfTime = System.nanoTime();
            double startDecoderTime = System.nanoTime();
            Map<String, Bitmap> output = runDecoders(featureMap);
            System.out.println("OSDIFOHLS decoder time: " + ((System.nanoTime() - startDecoderTime) / 1_000_000) + " ms");

            long endPPTime = System.nanoTime();
            if (numFrames == 1) {
                avgTime = endInfTime - startInfTime;
                avgTimePP = endPPTime - startPPTime;
            } else {
                avgTime = ((avgTime * (numFrames - 1)) / numFrames) + ((endInfTime - startInfTime) / numFrames);
                avgTimePP = ((avgTimePP * (numFrames - 1)) / numFrames) + ((endPPTime - startPPTime) / numFrames);
            }
            return output;
        }

        public OrtSession.Result runEncoder(Bitmap input) throws OrtException {
            OnnxTensor inp = ImageConversionUtil.bitmapToTensor(input, env);
            Map<String, ? extends OnnxTensorLike> inputs = Map.of(inputName, inp);
            return encoder.run(inputs);
        }

        public Map<String, Bitmap> runDecoders(OrtSession.Result featureMap) throws OrtException {
            // convert featureMap
            System.out.println("WADSGOIJ: " + featureMap);
            OnnxTensor inputTensor = null;
            for (Map.Entry<String, OnnxValue> entry : featureMap) {
                String key = entry.getKey();
                OnnxValue value = entry.getValue();
                if (Objects.equals(key, "last_hidden_state")) {
                    inputTensor = (OnnxTensor) value;
                }
                System.out.println("WADSGOIJ key: " + key);
                System.out.println("WADSGOIJ value: " + value);
            }
            if (inputTensor == null) {
                return null;
            }
            Map<String, Bitmap> output = new ConcurrentHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(decoders.length);
            ExecutorCompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
            try {
                for (OrtSession decoder : decoders) {
                    OnnxTensor finalInputTensor = inputTensor;
                    completionService.submit(() -> {
                        Map<String, ? extends OnnxTensorLike> input = Map.of("last_hidden_state", finalInputTensor);
                        OrtSession.Result result = decoder.run(input);
                        for (Map.Entry<String, OnnxValue> entry : result) {
                            System.out.println("WADSGOIJ decoder output: " + entry);
                            String key = entry.getKey();
                            OnnxValue value = entry.getValue();
                            if (value.getType() != OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                                System.err.println("ONNX ERROR: Value isn't in tensor.");
                                throw new RuntimeException("Non-tensor value encountered for key: " + key);
                            }
                            float[][][][] ft = (float[][][][]) value.getValue();
                            Bitmap bm = ConversionUtil.FloatArrayToImage(
                                    ft,
                                    ConversionUtil.stringToConversionMethod(
                                            Objects.requireNonNull(outputMappings.getOrDefault(key, ""))
                                    )
                            );
                            output.put(key, bm);
                        }
                        return null;
                    });
                }
                int completedTasks = 0;
                while (completedTasks < decoders.length) {
                    Future<Void> future = completionService.take();
                    completedTasks++;
                    try {
                        future.get();
                    } catch (ExecutionException e) {
                        Objects.requireNonNull(e.getCause()).printStackTrace();
                        executor.shutdownNow(); // Attempt to cancel remaining tasks
                        return null;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                return null;
            } finally {
                executor.shutdown();
            }
            return output;
        }

        public HardwareMetrics finishExecuteAndMonitor() {
            synchronized (dataLock) {
                started = false;

                // Get post-execution metrics
                double endTime = System.nanoTime();
                HardwareMetrics endMetrics = hardwareMonitor.getBaseMetrics();
                BatteryStats endBatteryStats = hardwareMonitor.getBatteryStats();

                // Calculate execution time
                double totalExecutionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                double fps = numFrames / (totalExecutionTime / 1_000);
                double avgExecution = avgTime / 1_000_000;
                double avgExecutionWithProcessing = avgTimePP / 1_000_000;
                double cpuUsageDelta = endMetrics.cpuUsagePercent - startMetrics.cpuUsagePercent;
                long memoryUsedBytes = endMetrics.memoryUsedBytes - startMetrics.memoryUsedBytes;

                // return metrics
                return new HardwareMetrics(
                        avgExecution,
                        avgExecutionWithProcessing,
                        fps,
                        totalExecutionTime,
                        numFrames,
                        endMetrics.cpuUsagePercent,
                        cpuUsageDelta,
                        endMetrics.threadCpuTimeMs,
                        memoryUsedBytes,
                        startBattery,
                        endBatteryStats
                );
            }
        }

        public int getNumFrames() { return numFrames; }

        public double getCurrentTime() {
            return (System.nanoTime() - startTime) / 1_000_000_000;
        }

        public boolean hasStarted() { return started; }

        public void setStarted(boolean newStarted) { started = newStarted; }
    }

    public static class PyTorchModelMonitor {
        private final Module model;
        private OrtSession session;
        private final OrtEnvironment env;
        private final Settings settings;
        private final String inputName;
        private final HardwareMonitor hardwareMonitor;
        private final boolean outputDict;
        private long lastExecutionTime = 0;
        private final Map<String, String> outputMappings;
        private HardwareMetrics startMetrics;
        private BatteryStats startBattery;
        private double startTime;
        private double startCpuUsage;
        private boolean started;
        private int numFrames;
        private double avgTime;
        private double avgTimePP;

        public PyTorchModelMonitor(Module model, Settings settings, Context context) {
            this.model = model;
            this.settings = settings;
            this.session = null;
            this.env = null;
            this.hardwareMonitor = new HardwareMonitor(context);
            this.inputName = "input";
            outputDict = false;
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }
            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            outputMappings = gson.fromJson(json, type);
            started = false;
        }

        public PyTorchModelMonitor(OrtSession model, OrtEnvironment env, Settings settings, Context context) {
            this.model = null;
            this.settings = settings;
            this.session = model;
            this.env = env;
            this.hardwareMonitor = new HardwareMonitor(context);
            this.inputName = "input";
            outputDict = true;
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }
            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            outputMappings = gson.fromJson(json, type);
            started = false;
        }

        public PyTorchModelMonitor(
                OrtSession model,
                OrtEnvironment env,
                Settings settings,
                String inputName,
                Context context) {
            this.model = null;
            this.settings = settings;
            this.session = model;
            this.env = env;
            this.hardwareMonitor = new HardwareMonitor(context);
            this.inputName = inputName;
            outputDict = true;
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }
            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            outputMappings = gson.fromJson(json, type);
            started = false;
        }

        public void startExecuteAndMonitor() {
            started = true;
            startMetrics = hardwareMonitor.getBaseMetrics();
            startBattery = hardwareMonitor.getBatteryStats();
            startTime = System.nanoTime();
            numFrames = 0;
            avgTime = 0;
            avgTimePP = 0;
            startCpuUsage = startMetrics.cpuUsagePercent;
        }

        public Map<String, Bitmap> runInference(Bitmap input) throws OrtException {
            if (!started) {
                System.out.println("CANNOT RUN INFERENCE WITHOUT STARTING METRICS");
                return null;
            }
            double startInfTime;
            double endInfTime;
            double startPPTime = System.nanoTime();
            numFrames++;
            // Execute model
            Map<String, Bitmap> output = new HashMap<>();
            if (outputDict) {
                if (session == null) {
                    System.err.println("ERROR: Session should not be null.");
                    return null;
                }
                OnnxTensor inp = ImageConversionUtil.bitmapToTensor(input, env);
                Map<String, ? extends OnnxTensorLike> inputs = Map.of(inputName, inp);
                startInfTime = System.nanoTime();
                OrtSession.Result outputs = session.run(inputs);
                endInfTime = System.nanoTime();
                for (Map.Entry<String, OnnxValue> entry : outputs) {
                    String key = entry.getKey();
                    OnnxValue value = entry.getValue();
                    if (value.getType() == OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                        float[][][][] ft = (float[][][][]) value.getValue();
                        Bitmap bm = ConversionUtil.FloatArrayToImage(
                                ft,
                                ConversionUtil.stringToConversionMethod(
                                        Objects.requireNonNull(outputMappings.getOrDefault(key, ""))
                                )
                        );
                        output.put(key, bm);
                    } else {
                        System.err.println("ONNX ERROR: Value isn't in tensor.");
                        return null;
                    }
                }
                System.out.println("ONNX: " + output.toString());
            } else {
                if (model == null) {
                    System.err.println("ERROR: Model should not be null.");
                    return null;
                }
                Tensor inp = ConversionUtil.bitmapToTensor(input);
                startInfTime = System.nanoTime();
                Tensor out_val = model.forward(IValue.from(inp)).toTensor();
                endInfTime = System.nanoTime();
                String key = "output";
                output.put(key, ConversionUtil.TensorToImage(
                        out_val,
                        ConversionUtil.stringToConversionMethod(
                                Objects.requireNonNull(outputMappings.getOrDefault(key, ""))
                        ),
                        input.getWidth(),
                        input.getHeight())
                );
            }
            long endPPTime = System.nanoTime();
            if (numFrames == 1) {
                avgTime = endInfTime - startInfTime;
                avgTimePP = endPPTime - startPPTime;
            } else {
                avgTime = ((avgTime * (numFrames - 1)) / numFrames) + ((endInfTime - startInfTime) / numFrames);
                avgTimePP = ((avgTimePP * (numFrames - 1)) / numFrames) + ((endPPTime - startPPTime) / numFrames);
            }
            return output;
        }

        public HardwareMetrics finishExecuteAndMonitor() {
            started = false;

            // Get post-execution metrics
            double endTime = System.nanoTime();
            HardwareMetrics endMetrics = hardwareMonitor.getBaseMetrics();
            BatteryStats endBatteryStats = hardwareMonitor.getBatteryStats();

            // Calculate execution time
            double totalExecutionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            double fps = numFrames / (totalExecutionTime / 1_000);
            double avgExecution = avgTime / 1_000_000;
            double avgExecutionWithProcessing = avgTimePP / 1_000_000;
            double cpuUsageDelta = endMetrics.cpuUsagePercent - startMetrics.cpuUsagePercent;
            long memoryUsedBytes = endMetrics.memoryUsedBytes - startMetrics.memoryUsedBytes;

            // return metrics
            return new HardwareMetrics(
                    avgExecution,
                    avgExecutionWithProcessing,
                    fps,
                    totalExecutionTime,
                    numFrames,
                    endMetrics.cpuUsagePercent,
                    cpuUsageDelta,
                    endMetrics.threadCpuTimeMs,
                    memoryUsedBytes,
                    startBattery,
                    endBatteryStats
            );
        }

        public int getNumFrames() { return numFrames; }

        public double getCurrentTime() {
            return (System.nanoTime() - startTime) / 1_000_000_000;
        }

        public boolean hasStarted() { return started; }

        public void setStarted(boolean newStarted) { started = newStarted; }

        public HardwareMetrics executeAndMonitor(Bitmap input) throws OrtException {
            System.out.println("ONNX: " + Debug.getRuntimeStats());
            System.out.println("ONNX should get runtime: " + prefs.getBoolean("runtime_model", true));

            // Get pre-execution metrics
            HardwareMetrics preMetrics = hardwareMonitor.getBaseMetrics();
            BatteryStats startBatteryStats = hardwareMonitor.getBatteryStats();
            long startWithProcessing = System.nanoTime();
            long startTime = 0;
            long endTime = 0;

            // Execute model
            Map<String, Bitmap> output = new HashMap<>();
            if (outputDict) {
                if (session == null) {
                    System.err.println("ERROR: Session should not be null.");
                    return null;
                }
                OnnxTensor inp = ImageConversionUtil.bitmapToTensor(input, env);
                Map<String, ? extends OnnxTensorLike> inputs = Map.of(inputName, inp);
                startTime = System.nanoTime();
                OrtSession.Result outputs = session.run(inputs);
                endTime = System.nanoTime();
                for (Map.Entry<String, OnnxValue> entry : outputs) {
                    String key = entry.getKey();
                    OnnxValue value = entry.getValue();
                    if (value.getType() == OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                        float[][][][] ft = (float[][][][]) value.getValue();
                        Bitmap bm = ConversionUtil.FloatArrayToImage(
                                ft,
                                ConversionUtil.stringToConversionMethod(
                                        Objects.requireNonNull(outputMappings.getOrDefault(key, ""))
                                )
                        );
                        output.put(key, bm);
                    } else {
                        System.err.println("ONNX ERROR: Value isn't in tensor.");
                        return null;
                    }
                }
                System.out.println("ONNX: " + output.toString());
            } else {
                if (model == null) {
                    System.err.println("ERROR: Model should not be null.");
                    return null;
                }
                Tensor inp = ConversionUtil.bitmapToTensor(input);
                startTime = System.nanoTime();
                Tensor out_val = model.forward(IValue.from(inp)).toTensor();
                endTime = System.nanoTime();
                String key = "output";
                output.put(key, ConversionUtil.TensorToImage(
                        out_val,
                        ConversionUtil.stringToConversionMethod(
                                Objects.requireNonNull(outputMappings.getOrDefault(key, ""))
                        ),
                        input.getWidth(),
                        input.getHeight())
                );
            }

            // Get post-execution metrics
            long endWithProcessing = System.nanoTime();
            HardwareMetrics postMetrics = hardwareMonitor.getBaseMetrics();
            BatteryStats endBatteryStats = hardwareMonitor.getBatteryStats();

            // Calculate execution time
            long executionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            long executionWithProcessing = (endWithProcessing - startWithProcessing) / 1_000_000;
            double cpuUsageDelta = postMetrics.cpuUsagePercent - preMetrics.cpuUsagePercent;
            long memoryUsedBytes = postMetrics.memoryUsedBytes - preMetrics.memoryUsedBytes;

            // Store metrics
            HardwareMetrics metrics = new HardwareMetrics(
                    executionTime,
                    executionWithProcessing,
                    1.0 / (executionWithProcessing / 1_000.0),
                    executionWithProcessing,
                    1,
                    postMetrics.cpuUsagePercent,
                    cpuUsageDelta,
                    postMetrics.threadCpuTimeMs,
                    memoryUsedBytes,
                    startBatteryStats,
                    endBatteryStats
            );
            metrics.output = output;

            lastExecutionTime = executionTime;
            return metrics;
        }

        public long getLastExecutionTime() {
            return lastExecutionTime;
        }

        public void changeSession(OrtSession newSession) {
            session = newSession;
        }
    }
}
