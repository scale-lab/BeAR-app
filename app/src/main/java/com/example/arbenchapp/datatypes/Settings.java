package com.example.arbenchapp.datatypes;

import java.util.Map;

public class Settings {

    private final Map<String, ConversionMethod> conversionMap;
    private final RunType runType;
    private final int imgHeight;
    private final int imgWidth;
    private final boolean dimsInit;


    public Settings(RunType runType, Map<String, ConversionMethod> conversionMap) {
        this.conversionMap = conversionMap;
        this.runType = runType;
        this.imgHeight = 0;
        this.imgWidth = 0;
        this.dimsInit = false;
    }
    public Settings(
            RunType runType,
            Map<String, ConversionMethod> conversionMap,
            int imgHeight, int imgWidth) {
        this.conversionMap = conversionMap;
        this.runType = runType;
        this.imgHeight = imgHeight;
        this.imgWidth = imgWidth;
        this.dimsInit = true;
    }

    public ConversionMethod getConversion(String key) {
        return this.conversionMap.getOrDefault(key, ConversionMethod.DEFAULT);
    }

    public RunType getRunType() {
        return runType;
    }

    public boolean isDimsInit() { return dimsInit; }

    public int getImgHeight() { return imgHeight; }

    public int getImgWidth() { return imgWidth; }

}
