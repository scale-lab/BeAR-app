package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

import com.example.arbenchapp.MTLBox;
import com.example.arbenchapp.monitor.HardwareMonitor;
import com.example.arbenchapp.util.ConversionUtil;

import org.pytorch.Tensor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MTLBoxStruct {
    private final Map<String, Bitmap> bitmaps;
    private final RunType runType;
    private final Bitmap input;
    private final Double ms;
    private HardwareMonitor.HardwareMetrics metrics = null;

    private Map<String, Bitmap> convert(Map<String, Tensor> tensors, int w, int h) {
        Map<String, Bitmap> bitmaps = new HashMap<>();
        Map<String, ConversionMethod> conversionMap = ConversionUtil.getConversionMap(runType);
        for (Map.Entry<String, Tensor> entry : tensors.entrySet()) {
            Bitmap bm = ConversionUtil.TensorToImage(entry.getValue(),
                    Objects.requireNonNull(conversionMap.getOrDefault(entry.getKey(), ConversionMethod.DEFAULT)),
                    w, h);
            bitmaps.put(entry.getKey(), bm);
        }
        return bitmaps;
    }

    public MTLBoxStruct(Map<String, Bitmap> bitmaps, RunType runType, Bitmap input, Double ms, HardwareMonitor.HardwareMetrics metrics) {
        this.bitmaps = bitmaps;
        this.runType = runType;
        this.input = input;
        this.ms = ms;
        this.metrics = metrics;
    }

    public MTLBoxStruct(Map<String, Tensor> tensors, RunType runType, Bitmap input, Double ms, HardwareMonitor.HardwareMetrics metrics, int w, int h) {
        this.runType = runType;
        this.bitmaps = convert(tensors, w, h);
        this.input = input;
        this.ms = ms;
        this.metrics = metrics;
    }

    public Bitmap getInput() { return this.input; }

    public Map<String, Bitmap> getBitmaps() { return this.bitmaps; }

    public Double getTime() {
        return this.ms;
    }

    public HardwareMonitor.HardwareMetrics getMetrics() { return this.metrics; }
}
