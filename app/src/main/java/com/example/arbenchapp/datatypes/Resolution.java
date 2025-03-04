package com.example.arbenchapp.datatypes;

public class Resolution {
    private final int width;
    private final int height;

    public Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public Resolution(String resolution) {
        String[] parts = resolution.split(",");
        if (parts.length != 2) {
            System.err.println("ERROR: INVALID RESOLUTION STRING!");
            this.width = 224;
            this.height = 224;
            return;
        }
        this.width = Integer.parseInt(parts[0].trim());
        this.height = Integer.parseInt(parts[1].trim());
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}
