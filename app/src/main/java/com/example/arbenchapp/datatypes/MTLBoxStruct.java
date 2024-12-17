package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

public class MTLBoxStruct {
    private final Bitmap bm;
    private final Double ms;

    public MTLBoxStruct(Bitmap bm, Double ms) {
        this.bm = bm;
        this.ms = ms;
    }

    public Bitmap getBitmap() {
        return this.bm;
    }

    public Double getTime() {
        return this.ms;
    }
}
