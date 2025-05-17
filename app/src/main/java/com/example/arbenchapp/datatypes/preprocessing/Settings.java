package com.example.arbenchapp.datatypes.preprocessing;

public class Settings {

    private final int imgHeight;
    private final int imgWidth;
    private final boolean dimsInit;


    public Settings() {
        this.imgHeight = 0;
        this.imgWidth = 0;
        this.dimsInit = false;
    }
    public Settings(int imgHeight, int imgWidth) {
        this.imgHeight = imgHeight;
        this.imgWidth = imgWidth;
        this.dimsInit = true;
    }

    public boolean isDimsInit() { return dimsInit; }

    public int getImgHeight() { return imgHeight; }

    public int getImgWidth() { return imgWidth; }

}
