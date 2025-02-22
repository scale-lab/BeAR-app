package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

public class ImagePage {
    private final Bitmap image;
    private final String caption;

    public ImagePage(Bitmap image, String caption) {
        this.image = image;
        this.caption = caption;
    }

    public Bitmap getImage() {
        return this.image;
    }

    public String getCaption() {
        return this.caption;
    }
}
