package com.example.arbenchapp.improvemodels;

import android.graphics.Bitmap;
import android.graphics.Color;

public class BitmapPool {
    private final Bitmap[] pool;
    private final boolean[] inUse;
    private final int width, height;
    private final Bitmap.Config config;

    public BitmapPool(int size, int width, int height, Bitmap.Config config) {
        this.pool = new Bitmap[size];
        this.inUse = new boolean[size];
        this.width = width;
        this.height = height;
        this.config = config;
    }

    public synchronized Bitmap acquire() {
        for (int i = 0; i < pool.length; i++) {
            if (!inUse[i]) {
                if (pool[i] == null || pool[i].isRecycled() ||
                        pool[i].getWidth() != width ||
                        pool[i].getHeight() != height) {
                    if (pool[i] != null && !pool[i].isRecycled()) {
                        pool[i].recycle();
                    }
                    pool[i] = Bitmap.createBitmap(width, height, config);
                }
                inUse[i] = true;
                return pool[i];
            }
        }
        return Bitmap.createBitmap(width, height, config);
    }

    public synchronized void release(Bitmap bitmap) {
        if (bitmap == null) return;

        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == bitmap) {
                inUse[i] = false;
                bitmap.eraseColor(Color.TRANSPARENT);
                return;
            }
        }
        // Not from our pool - recycle it
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public synchronized void clear() {
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] != null && !pool[i].isRecycled()) {
                pool[i].recycle();
                pool[i] = null;
            }
            inUse[i] = false;
        }
    }
}
