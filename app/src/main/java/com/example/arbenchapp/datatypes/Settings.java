package com.example.arbenchapp.datatypes;

public class Settings {

    private final RunType runType;
    private final int imgHeight;
    private final int imgWidth;
    private final boolean dimsInit;


    public Settings(RunType runType) {
        this.runType = runType;
        this.imgHeight = 0;
        this.imgWidth = 0;
        this.dimsInit = false;
    }
    public Settings(RunType runType, int imgHeight, int imgWidth) {
        this.runType = runType;
        this.imgHeight = imgHeight;
        this.imgWidth = imgWidth;
        this.dimsInit = true;
    }

    public RunType getRunType() {
        return runType;
    }

    public boolean isDimsInit() { return dimsInit; }

    public int getImgHeight() { return imgHeight; }

    public int getImgWidth() { return imgWidth; }

}
