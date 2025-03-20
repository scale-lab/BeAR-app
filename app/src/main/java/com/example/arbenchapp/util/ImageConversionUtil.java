package com.example.arbenchapp.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.pytorch.Tensor;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public final class ImageConversionUtil {
    private ImageConversionUtil() {}

    private static int[][] colors = {
            {53, 13, 51},
            {160, 22, 50},
            {122, 53, 68},
            {221, 80, 51},
            {136, 142, 48},
            {78, 34, 68},
            {224, 164, 230},
            {167, 178, 127},
            {152, 213, 44},
            {125, 122, 149},
            {47, 146, 30},
            {177, 86, 183},
            {62, 42, 251},
            {139, 202, 160},
            {180, 136, 48},
            {176, 140, 195},
            {96, 180, 188},
            {250, 229, 244},
            {123, 209, 21},
            {161, 56, 207},
            {11, 124, 56},
            {202, 177, 144},
            {22, 109, 244},
            {197, 35, 16},
            {57, 98, 84},
            {20, 20, 198},
            {146, 17, 147},
            {94, 190, 155},
            {228, 89, 199},
            {191, 53, 182},
            {24, 188, 15},
            {235, 218, 18},
            {202, 16, 163},
            {106, 239, 196},
            {70, 110, 27},
            {7, 136, 154},
            {232, 32, 56},
            {228, 233, 202},
            {237, 0, 35},
            {203, 233, 240}
    };

    static {
        System.loadLibrary("native-lib"); // Load the native library
    }

    private static native void convertToBitmapNative(float[] data, int layers, int channels, int height, int width, Bitmap bitmap, int[][] colors);

    private static native void convertToGrayscale(float[] data, int height, int width, Bitmap bitmap);
//    public static Bitmap DefaultConvert() {
//
//    }

    public static Bitmap BWConvert(Tensor tensor, int width, int height) {
        // Step 1: Extract tensor data as a float array
        float[] tensorData = tensor.getDataAsFloatArray();

        // Step 2: Create an empty bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int numClasses = 21; // Number of classes in the output tensor
        int[] pixels = new int[width * height];

        // Step 3: Iterate through each pixel and find the class with the maximum value
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                int maxClassIndex = 0;
                float maxValue = tensorData[pixelIndex];

                // Compare logits for all classes at this pixel
                for (int c = 1; c < numClasses; c++) {
                    float value = tensorData[c * width * height + pixelIndex];
                    if (value > maxValue) {
                        maxValue = value;
                        maxClassIndex = c;
                    }
                }

                // Convert class index to grayscale (scale between 0-255)
                int gray = (int) ((maxClassIndex / (float) (numClasses - 1)) * 255);
                pixels[pixelIndex] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            }
        }

        // Step 4: Set pixel data into the bitmap
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;
    }

    public static Bitmap ColorConvert(Tensor tensor, int width, int height) {
        try {
            // Get tensor data as float array
            float[] tensorValues = tensor.getDataAsFloatArray();

            // Denormalize the tensor values
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;

                    // Extract RGB values and denormalize
                    float r = Math.min(255, Math.max(0,
                            (tensorValues[idx] * 0.229f + 0.485f) * 255));
                    float g = Math.min(255, Math.max(0,
                            (tensorValues[idx + width * height] * 0.224f + 0.456f) * 255));
                    float b = Math.min(255, Math.max(0,
                            (tensorValues[idx + 2 * width * height] * 0.225f + 0.406f) * 255));

                    // Combine into ARGB pixel
                    pixels[idx] = 0xFF000000 |
                            ((int)r << 16) |
                            ((int)g << 8) |
                            (int)b;
                }
            }

            // Create bitmap from pixel array
            Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            return resultBitmap;
        } catch (Exception e) {
            System.err.println("Error converting tensor to bitmap: " + e);
            return null;
        }
    }

    private static float[][][] transposeData(float[][][] data, int channels, int height, int width) {
        float[][][] transposed = new float[height][width][channels];
        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    transposed[h][w][c] = data[c][h][w];
                }
            }
        }
        return transposed;
    }

    private static float[] flatten(float[][][][] data, int layers, int channels, int height, int width) {
        float[] flatData = new float[layers * channels * height * width];
        for (int l = 0; l < layers; l++) {
            for (int c = 0; c < channels; c++) {
                for (int h = 0; h < height; h++) {
                    System.arraycopy(data[l][c][h], 0, flatData, (l * channels * height * width) + (c * height * width) + (h * width), width);
                }
            }
        }
        return flatData;
    }

    public static float[] flatten(float[][][] data, int channels, int height, int width) {
        float[] flatData = new float[channels * height * width];
        int index = 0;
        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    flatData[index++] = data[c][h][w];
                }
            }
        }
        return flatData;
    }

    public static Bitmap BWConvert(float[][][][] data) {
        int channels = data[0].length;
        assert channels == 1;
        int height = data[0][0].length;
        int width = data[0][0][0].length;

        double startTime = System.nanoTime();
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // only works on last layer rn
        for (float[][][] layer : data) {
            float[] flatData = flatten(layer, 1, height, width);
            convertToGrayscale(flatData, height, width, bm);
//            for (int h = 0; h < height; h++) {
//                for (int w = 0; w < width; w++) {
//                    int g = (int) (layer[0][h][w] * 255.0);
//                    bm.setPixel(w, h, Color.argb(255, g, g, g));
//                }
//            }
        }
        double endTime = System.nanoTime();
        System.out.println("TIMEEC bw convert (ms): " + ((endTime - startTime) / 1_000_000));
        return bm;
    }

    public static Bitmap ColorConvert(float[][][][] data) throws InterruptedException {
        int layers = data.length;
        int channels = data[0].length;
        int height = data[0][0].length;
        int width = data[0][0][0].length;
        // only works on last layer rn
        double startTime = System.nanoTime();
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        float[] flatData = flatten(data, layers, channels, height, width);
        convertToBitmapNative(flatData, layers, channels, height, width, bm, colors);
        double endTime = System.nanoTime();
        System.out.println("TIMEEC color convert (ms): " + ((endTime - startTime) / 1_000_000));
        return bm;
    }
}
