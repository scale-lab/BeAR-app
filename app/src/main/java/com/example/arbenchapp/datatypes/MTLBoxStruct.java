package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

import com.example.arbenchapp.MTLBox;
import com.example.arbenchapp.monitor.HardwareMonitor;

import org.pytorch.Tensor;

import java.util.HashMap;
import java.util.Map;

public class MTLBoxStruct {
    private final Map<String, Bitmap> bitmaps;
    private final RunType runType;
    private final Bitmap input;
    private final Double ms;
    private HardwareMonitor.HardwareMetrics metrics = null;

    private Map<String, Bitmap> convert(Map<String, Tensor> tensors, int w, int h) {
        Map<String, Bitmap> bitmaps = new HashMap<>();
        Settings s = new Settings(RunType.MTL, h, w);
        MTLBox box = new MTLBox(s);
        for (Map.Entry<String, Tensor> entry : tensors.entrySet()) {
            Bitmap bm = runType != RunType.DEEPLABV3 ?
                    box.TensorToBitmap(entry.getValue(), w, h) :
                    box.TensorToBW(entry.getValue(), w, h);
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
