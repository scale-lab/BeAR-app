package com.example.arbenchapp.monitor;

import android.graphics.Bitmap;

import java.util.Map;

public interface ProcessingResultListener {
    void onProcessingComplete(Bitmap input, Map<String, Bitmap> output);
    void requestNextFrame();
}
