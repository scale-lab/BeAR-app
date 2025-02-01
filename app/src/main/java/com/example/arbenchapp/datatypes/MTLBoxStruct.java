package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

import com.example.arbenchapp.monitor.HardwareMonitor;

public class MTLBoxStruct {
    private final Bitmap bm;
    private final Double ms;
    private HardwareMonitor.HardwareMetrics metrics = null;

    public MTLBoxStruct(Bitmap bm, Double ms, HardwareMonitor.HardwareMetrics metrics) {
        this.bm = bm;
        this.ms = ms;
        this.metrics = metrics;
    }

    public Bitmap getBitmap() {
        return this.bm;
    }

    public Double getTime() {
        return this.ms;
    }

    public HardwareMonitor.HardwareMetrics getMetrics() { return this.metrics; }
}
