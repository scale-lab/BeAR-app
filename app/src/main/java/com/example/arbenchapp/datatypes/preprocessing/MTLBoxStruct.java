package com.example.arbenchapp.datatypes.preprocessing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.preference.PreferenceManager;

import com.example.arbenchapp.monitor.HardwareMonitor;
import com.example.arbenchapp.util.ConversionUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.pytorch.Tensor;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MTLBoxStruct {
    private final Map<String, Bitmap> bitmaps;
    private final Bitmap input;
    private final Double ms;
    private boolean newMetrics;
    private HardwareMonitor.HardwareMetrics metrics = null;

    private Map<String, Bitmap> convert(Map<String, Tensor> tensors, Context context, int w, int h) {
        Map<String, Bitmap> bitmaps = new HashMap<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString("output_option_mappings", "");
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<String, String>>(){}.getType();
        Map<String, String> mappings = gson.fromJson(json, type);
        for (Map.Entry<String, Tensor> entry : tensors.entrySet()) {
            Bitmap bm = ConversionUtil.TensorToImage(entry.getValue(),
                    Objects.requireNonNull(
                            ConversionUtil.stringToConversionMethod(
                                    Objects.requireNonNull(
                                            mappings.getOrDefault(entry.getKey(), "")))),
                    w, h);
            bitmaps.put(entry.getKey(), bm);
        }
        return bitmaps;
    }

    public MTLBoxStruct(Map<String, Bitmap> bitmaps, Bitmap input, Double ms, HardwareMonitor.HardwareMetrics metrics) {
        this.bitmaps = bitmaps;
        this.input = input;
        this.ms = ms;
        this.newMetrics = true;
        this.metrics = metrics;
    }

    public MTLBoxStruct(Map<String, Bitmap> bitmaps, Bitmap input, Double ms) {
        this.bitmaps = bitmaps;
        this.input = input;
        this.ms = ms;
        this.newMetrics = false;
        this.metrics = null;
    }

    public MTLBoxStruct(Map<String, Tensor> tensors, Bitmap input, Context context, Double ms, HardwareMonitor.HardwareMetrics metrics, int w, int h) {
        this.bitmaps = convert(tensors, context, w, h);
        this.input = input;
        this.ms = ms;
        this.newMetrics = true;
        this.metrics = metrics;
    }

    public Bitmap getInput() { return this.input; }

    public Map<String, Bitmap> getBitmaps() { return this.bitmaps; }

    public Double getTime() {
        return this.ms;
    }

    public boolean hasOldMetrics() { return !this.newMetrics; }

    public HardwareMonitor.HardwareMetrics getMetrics() { return this.metrics; }
}
