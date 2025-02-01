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
import org.pytorch.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
        public double executionTimeMs;
        public double cpuUsagePercent;
        public double cpuUsageDelta;
        public double memoryUsageMB;
        public double memoryDeltaMB;
        public double threadCpuTimeMs;
        public Tensor output;

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "Execution Time: %.2f ms, CPU Usage Delta: %.2f, Memory Usage: %.2f MB, Memory Delta: %.2f, Thread CPU Time: %.2f ms",
                    executionTimeMs, cpuUsageDelta, memoryUsageMB, memoryDeltaMB, threadCpuTimeMs);
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
        private final HardwareMonitor hardwareMonitor;
        private long lastExecutionTime = 0;

        public PyTorchModelMonitor(Module model, Context context) {
            this.model = model;
            this.hardwareMonitor = new HardwareMonitor(context);
        }

        public HardwareMetrics executeAndMonitor(IValue input) {
            HardwareMetrics metrics = new HardwareMetrics();

            // Get pre-execution metrics
            HardwareMetrics preMetrics = hardwareMonitor.getMetrics();
            long startTime = System.nanoTime();

            // Execute model
            Tensor output = model.forward(input).toTensor();

            // Get post-execution metrics
            long endTime = System.nanoTime();
            HardwareMetrics postMetrics = hardwareMonitor.getMetrics();

            // Calculate execution time
            long executionTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            double cpuUsageDelta = postMetrics.cpuUsagePercent - preMetrics.cpuUsagePercent;
            System.out.println("CONV2D .. Post percent: " + postMetrics.cpuUsagePercent +
                    ", pre percent: " + preMetrics.cpuUsagePercent);
            double memoryDelta = postMetrics.memoryUsageMB - preMetrics.memoryUsageMB;

            // Store metrics
            metrics.executionTimeMs = executionTime;
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
