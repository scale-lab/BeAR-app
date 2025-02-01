package com.example.arbenchapp.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.privacysandbox.tools.core.model.Type;

import org.pytorch.Module;
import org.pytorch.IValue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        public double cpuUsagePercent;
        public double memoryUsageMB;
        public boolean isUsingGPU;
        public Map<String, Long> modelExecutionStats;

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "CPU Usage: %.2f%%, Memory: %.2f MB, GPU: %b, Model Stats: %s",
                    cpuUsagePercent, memoryUsageMB, isUsingGPU, modelExecutionStats.toString());
        }
    }

    public HardwareMetrics getMetrics() {
        HardwareMetrics metrics = new HardwareMetrics();
        metrics.cpuUsagePercent = getCpuUsage();
        metrics.memoryUsageMB = getMemoryUsage();
        metrics.isUsingGPU = false; //checkGPUUsage();
        metrics.modelExecutionStats = new HashMap<>();
        return metrics;
    }

    private double getCpuUsage() {
        try {
            long[] cpuUsage = new long[2];
            if (readCPUUsage(cpuUsage)) {
                long cpuTime = cpuUsage[0];
                long appTime = cpuUsage[1];

                if (lastCpuTime == 0 && lastAppCpuTime == 0) {
                    lastCpuTime = cpuTime;
                    lastAppCpuTime = appTime;
                    return 0;
                }

                double cpuUse = 100.0 * (appTime - lastAppCpuTime) / (cpuTime - lastCpuTime);
                lastCpuTime = cpuTime;
                lastAppCpuTime = appTime;
                return cpuUse;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting CPU usage", e);
        }
        return 0;
    }

    private boolean readCPUUsage(long[] usage) {
        try {
            File[] files = new File("/proc").listFiles();
            if (files != null) {
                long totalTime = 0;
                long appTime = 0;

                // Read total CPU time
                File stat = new File("/proc/stat");
                if (stat.exists()) {
                    // Implementation of reading /proc/stat
                    // This is a simplified version - you'd need to parse the actual file
                    totalTime = System.currentTimeMillis(); // placeholder
                }

                // Read app CPU time
                File appStat = new File("/proc/" + pid + "/stat");
                if (appStat.exists()) {
                    // Implementation of reading app's stat
                    // This is a simplified version - you'd need to parse the actual file
                    appTime = Debug.threadCpuTimeNanos() / 1000000L;
                }

                usage[0] = totalTime;
                usage[1] = appTime;
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading CPU usage", e);
        }
        return false;
    }

    private double getMemoryUsage() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);

        return memInfo.getTotalPss() / 1024.0; // Convert to MB
    }

//    private boolean checkGPUUsage() {
//        // Note: This is a simplified check. For more accurate results,
//        // you'll need to use device-specific APIs or check PyTorch's internal state
//        return org.pytorch.Device.Type.CUDA.equals(org.pytorch.Device.Type.CUDA);
//    }

    public static class PyTorchModelMonitor {
        private final Module model;
        private final HardwareMonitor hardwareMonitor;
        private long lastExecutionTime = 0;

        public PyTorchModelMonitor(Module model, Context context) {
            this.model = model;
            this.hardwareMonitor = new HardwareMonitor(context);
        }

        public Map<String, Object> executeAndMonitor(IValue input) {
            Map<String, Object> metrics = new HashMap<>();
            long startTime = System.nanoTime();

            // Get pre-execution metrics
            HardwareMetrics preMetrics = hardwareMonitor.getMetrics();

            // Execute model
            IValue output = model.forward(input);

            // Get post-execution metrics
            HardwareMetrics postMetrics = hardwareMonitor.getMetrics();
            long endTime = System.nanoTime();

            // Calculate execution time
            long executionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds

            // Store metrics
            metrics.put("executionTimeMs", executionTime);
            metrics.put("cpuUsageDelta", postMetrics.cpuUsagePercent - preMetrics.cpuUsagePercent);
            metrics.put("memoryUsageMB", postMetrics.memoryUsageMB);
            metrics.put("isUsingGPU", postMetrics.isUsingGPU);

            lastExecutionTime = executionTime;
            return metrics;
        }

        public long getLastExecutionTime() {
            return lastExecutionTime;
        }
    }
}
