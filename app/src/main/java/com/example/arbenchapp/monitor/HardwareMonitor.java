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

import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.util.ConversionUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import androidx.preference.PreferenceManager;

import org.pytorch.Module;
import org.pytorch.IValue;
import org.pytorch.Tensor;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
                double cpuUsagePercent,
                double cpuUsageDelta,
                double threadCpuTimeMs,
                long memoryUsedBytes,
                BatteryStats startStats,
                BatteryStats endStats) {
            this.executionTimeMs = executionTimeMs;
            this.executionWithProcessing = executionWithProcessing;
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
    }

    public HardwareMetrics getBaseMetrics() {
        return new HardwareMetrics(
                0.0,
                0.0,
                getCpuUsage(),
                0.0,
                (double) getThreadCpuTimeMs(),
                (long) getAvailMemory(),
                getBatteryStats(),
                getBatteryStats());
    }

    private double getCpuUsage() {
        try {
            long currentCpuTime = Debug.threadCpuTimeNanos();
            double cpuDelta = (currentCpuTime - lastCpuTime) / 1_000_000.0;
            lastCpuTime = currentCpuTime;

            int processors = Runtime.getRuntime().availableProcessors();

            return (cpuDelta / (1000.0 * processors)) * 100.0;
        } catch (Exception e) {
            Log.e(TAG, "CONV2D .. Error reading CPU usage", e);
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

    public static class PyTorchModelMonitor {
        private final Module model;
        private final OrtSession session;
        private final OrtEnvironment env;
        private final Settings settings;
        private final HardwareMonitor hardwareMonitor;
        private final boolean outputDict;
        private long lastExecutionTime = 0;

        public PyTorchModelMonitor(Module model, Settings settings, Context context) {
            this.model = model;
            this.settings = settings;
            this.session = null;
            this.env = null;
            this.hardwareMonitor = new HardwareMonitor(context);
            outputDict = false;
        }

        public PyTorchModelMonitor(OrtSession model, OrtEnvironment env, Settings settings, Context context) {
            this.model = null;
            this.settings = settings;
            this.session = model;
            this.env = env;
            this.hardwareMonitor = new HardwareMonitor(context);
            outputDict = true;
        }

        public HardwareMetrics executeAndMonitor(Bitmap input) throws OrtException {
            if (prefs == null) {
                prefs = PreferenceManager.getDefaultSharedPreferences(this.hardwareMonitor.context);
            }

            String json = prefs.getString("output_option_mappings", "");
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            Map<String, String> outputMappings = gson.fromJson(json, type);

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
                OnnxTensor inp = ConversionUtil.bitmapToTensor(input, env);
                Map<String, ? extends OnnxTensorLike> inputs = Map.of("input", inp);
                startTime = System.nanoTime();
                OrtSession.Result outputs = session.run(inputs);
                endTime = System.nanoTime();
                for (Map.Entry<String, OnnxValue> entry : outputs) {
                    String key = entry.getKey();
                    OnnxValue value = entry.getValue();
                    if (value.getType() == OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                        float[][][][] ft = (float[][][][]) value.getValue();
                        System.out.println("ONNX " + key + ": " + Arrays.deepToString(ft));
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
    }
}
