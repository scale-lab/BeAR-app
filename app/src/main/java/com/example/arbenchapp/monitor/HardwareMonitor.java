package com.example.arbenchapp.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Debug;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import org.pytorch.Module;
import org.pytorch.IValue;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Locale;
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
    private final ActivityManager activityManager;
    private long lastCpuTime = 0;
    private long lastAppCpuTime = 0;
    private final int pid;

    public HardwareMonitor(Context context) {
        this.context = context;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.pid = Process.myPid();
    }

    public static class HardwareMetrics {
        public double executionTimeMs;
        public double executionWithProcessing;
        public double cpuUsagePercent;
        public double cpuUsageDelta;
        public double memoryUsageMB;
        public double memoryDeltaMB;
        public double threadCpuTimeMs;
        public Map<String, Tensor> output;

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "Execution Time: %.2f ms, Execution Time With Processing: %.2f ms, CPU Usage Delta: %.2f, Memory Usage: %.2f MB, Memory Delta: %.2f, Thread CPU Time: %.2f ms",
                    executionTimeMs, executionWithProcessing, cpuUsageDelta, memoryUsageMB, memoryDeltaMB, threadCpuTimeMs);
        }
    }

    public HardwareMetrics getMetrics() {
        HardwareMetrics metrics = new HardwareMetrics();
        metrics.executionTimeMs = 0.0;
        metrics.cpuUsagePercent = getCpuUsage();
        metrics.cpuUsageDelta = 0.0;
        metrics.memoryUsageMB = getMemoryUsage();
        metrics.memoryDeltaMB = 0.0;
        metrics.threadCpuTimeMs = getThreadCpuTimeMs();
        metrics.output = null;
        return metrics;
    }

    private static OnnxTensor bitmapToTensor(Bitmap bitmap, OrtEnvironment env) throws OrtException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channels = bitmap.hasAlpha() ? 4 : 3;

        // Convert bitmap pixels to a float array
        float[] floatValues = new float[width * height * channels];
        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        for (int i = 0; i < intValues.length; i++) {
            int pixel = intValues[i];
            floatValues[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
            floatValues[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f; // Green
            floatValues[i * 3 + 2] = (pixel & 0xFF) / 255.0f; // Blue
        }

        // Convert to FloatBuffer
        FloatBuffer buffer = FloatBuffer.wrap(floatValues);
        long[] shape = {1, channels, height, width}; // NCHW format

        // Create an OnnxTensor
        return OnnxTensor.createTensor(env, buffer, shape);
    }

    private static Tensor bitmapToTensor(Bitmap bitmap) {
        // Normalize the image using ImageNet mean and standard deviation
        float[] normMeanRGB = {0.485f, 0.456f, 0.406f};
        float[] normStdRGB = {0.229f, 0.224f, 0.225f};

        // Convert the bitmap to a float32 tensor
        return TensorImageUtils.bitmapToFloat32Tensor(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), normMeanRGB, normStdRGB
        );
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

    private double getMemoryUsage() {
        try {
            Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memInfo);

            return memInfo.getTotalPss() / 1024.0; // Convert to MB
        } catch (Exception e) {
            Log.e(TAG, "CONV2D .. Error getting memory usage", e);
            return 0.0;
        }
    }

    private long getThreadCpuTimeMs() {
        return Debug.threadCpuTimeNanos() / 1_000_000; // Convert to milliseconds
    }

    public static class PyTorchModelMonitor {
        private final Module model;
        private final OrtSession session;
        private final OrtEnvironment env;
        private final HardwareMonitor hardwareMonitor;
        private final boolean outputDict;
        private long lastExecutionTime = 0;

        public PyTorchModelMonitor(Module model, Context context) {
            this.model = model;
            this.session = null;
            this.env = null;
            this.hardwareMonitor = new HardwareMonitor(context);
            outputDict = false;
        }

        public PyTorchModelMonitor(OrtSession model, OrtEnvironment env, Context context) {
            this.model = null;
            this.session = model;
            this.env = env;
            this.hardwareMonitor = new HardwareMonitor(context);
            outputDict = true;
        }

        public HardwareMetrics executeAndMonitor(Bitmap input) throws OrtException {
            HardwareMetrics metrics = new HardwareMetrics();

            // Get pre-execution metrics
            HardwareMetrics preMetrics = hardwareMonitor.getMetrics();
            long startWithProcessing = System.nanoTime();
            long startTime = 0;
            long endTime = 0;

            // Execute model
            Map<String, Tensor> output = new HashMap<>();
            if (outputDict) {
                if (session == null) {
                    System.err.println("ERROR: Session should not be null.");
                    return null;
                }
                OnnxTensor inp = bitmapToTensor(input, env);
                Map<String, ? extends OnnxTensorLike> inputs = Map.of("input", inp);
                startTime = System.nanoTime();
                OrtSession.Result outputs = session.run(inputs);
                endTime = System.nanoTime();
                for (Map.Entry<String, OnnxValue> entry : outputs) {
                    String key = entry.getKey();
                    OnnxValue value = entry.getValue();
                    if (value.getType() == OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                        float[][][][] ft = (float[][][][]) value.getValue();
                        System.out.println("ONNX lengths: " + ft.length + ", " + ft[0].length + ", " + ft[0][0].length + ", " + ft[0][0][0].length);
                        int height = ft[0][0][0].length;
                        int width = ft[0][0].length;
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        int[] pixels = new int[height * width];
                        Map<Integer, Integer[]> colors = Map.ofEntries(
                                Map.entry(0, new Integer[] {255, 0, 0}),
                                Map.entry(1, new Integer[] {0, 0, 255}),
                                Map.entry(2, new Integer[] {0, 255, 0}),
                                Map.entry(3, new Integer[] {255, 255, 0}),
                                Map.entry(4, new Integer[] {0, 255, 255}),
                                Map.entry(5, new Integer[] {255, 0, 255}),
                                Map.entry(6, new Integer[] {255, 255, 255}),
                                Map.entry(7, new Integer[] {0, 0, 0}),
                                Map.entry(8, new Integer[] {0, 200, 255}),
                                Map.entry(9, new Integer[] {255, 200, 0})
                        );
                        for (float[][][] floats : ft) {
                            // right now, this only works for the last batch image
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    int colorInd = 0;
                                    float prob = floats[0][x][y];
                                    for (int c = 0; c < Math.min(ft[0].length, 10); c++) {
                                        if (floats[c][x][y] > prob) {
                                            colorInd = c;
                                            prob = floats[c][x][y];
                                        }
                                    }
                                    int r = Objects.requireNonNull(colors.get(colorInd))[0];
                                    int g = Objects.requireNonNull(colors.get(colorInd))[1];
                                    int b = Objects.requireNonNull(colors.get(colorInd))[2];
                                    pixels[y * width + x] = Color.rgb(r, g, b);
                                }
                            }
                        }
                        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
                        output.put(key, bitmapToTensor(bitmap));
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
                Tensor inp = bitmapToTensor(input);
                startTime = System.nanoTime();
                Tensor out_val = model.forward(IValue.from(inp)).toTensor();
                endTime = System.nanoTime();
                output.put("output", out_val);
            }

            // Get post-execution metrics
            long endWithProcessing = System.nanoTime();
            HardwareMetrics postMetrics = hardwareMonitor.getMetrics();

            // Calculate execution time
            long executionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            long executionWithProcessing = (endWithProcessing - startWithProcessing) / 1_000_000;
            double cpuUsageDelta = postMetrics.cpuUsagePercent - preMetrics.cpuUsagePercent;
            System.out.println("CONV2D .. Post percent: " + postMetrics.cpuUsagePercent +
                    ", pre percent: " + preMetrics.cpuUsagePercent);
            double memoryDelta = postMetrics.memoryUsageMB - preMetrics.memoryUsageMB;

            // Store metrics
            metrics.executionTimeMs = executionTime;
            metrics.executionWithProcessing = executionWithProcessing;
            metrics.cpuUsagePercent = postMetrics.cpuUsagePercent;
            metrics.cpuUsageDelta = cpuUsageDelta;
            metrics.memoryUsageMB = postMetrics.memoryUsageMB;
            metrics.memoryDeltaMB = memoryDelta;
            metrics.threadCpuTimeMs = postMetrics.threadCpuTimeMs;
            metrics.output = output;

            lastExecutionTime = executionTime;
            return metrics;
        }

        public long getLastExecutionTime() {
            return lastExecutionTime;
        }
    }
}
