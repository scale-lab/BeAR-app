package com.example.arbenchapp.datatypes;

import android.graphics.Bitmap;

public class ImagePage {
    private Bitmap image;
    private final String caption;

    public ImagePage(Bitmap image, String caption) {
        this.image = image.copy(image.getConfig(), true);
        this.caption = caption;
    }

    public Bitmap getImage() {
        return this.image.copy(this.image.getConfig(), true);
    }

    public void setImage(Bitmap image) {
        this.image = image.copy(image.getConfig(), true);
    }

    public String getCaption() {
        return this.caption;
    }

    public void recycle() {
        if (this.image != null && !this.image.isRecycled()) {
            this.image.recycle();
        }
    }
}
