package com.example.arbenchapp.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.pytorch.Tensor;

import java.util.Map;
import java.util.Objects;

public final class ImageConversionUtil {
    private ImageConversionUtil() {}

    private static final Map<Integer, Integer[]> colors = Map.ofEntries(
            Map.entry(0, new Integer[]{53, 13, 51}),
            Map.entry(1, new Integer[]{160, 22, 50}),
            Map.entry(2, new Integer[]{122, 53, 68}),
            Map.entry(3, new Integer[]{221, 80, 51}),
            Map.entry(4, new Integer[]{136, 142, 48}),
            Map.entry(5, new Integer[]{78, 34, 68}),
            Map.entry(6, new Integer[]{224, 164, 230}),
            Map.entry(7, new Integer[]{167, 178, 127}),
            Map.entry(8, new Integer[]{152, 213, 44}),
            Map.entry(9, new Integer[]{125, 122, 149}),
            Map.entry(10, new Integer[]{47, 146, 30}),
            Map.entry(11, new Integer[]{177, 86, 183}),
            Map.entry(12, new Integer[]{62, 42, 251}),
            Map.entry(13, new Integer[]{139, 202, 160}),
            Map.entry(14, new Integer[]{180, 136, 48}),
            Map.entry(15, new Integer[]{176, 140, 195}),
            Map.entry(16, new Integer[]{96, 180, 188}),
            Map.entry(17, new Integer[]{250, 229, 244}),
            Map.entry(18, new Integer[]{123, 209, 21}),
            Map.entry(19, new Integer[]{161, 56, 207}),
            Map.entry(20, new Integer[]{11, 124, 56}),
            Map.entry(21, new Integer[]{202, 177, 144}),
            Map.entry(22, new Integer[]{22, 109, 244}),
            Map.entry(23, new Integer[]{197, 35, 16}),
            Map.entry(24, new Integer[]{57, 98, 84}),
            Map.entry(25, new Integer[]{20, 20, 198}),
            Map.entry(26, new Integer[]{146, 17, 147}),
            Map.entry(27, new Integer[]{94, 190, 155}),
            Map.entry(28, new Integer[]{228, 89, 199}),
            Map.entry(29, new Integer[]{191, 53, 182}),
            Map.entry(30, new Integer[]{24, 188, 15}),
            Map.entry(31, new Integer[]{235, 218, 18}),
            Map.entry(32, new Integer[]{202, 16, 163}),
            Map.entry(33, new Integer[]{106, 239, 196}),
            Map.entry(34, new Integer[]{70, 110, 27}),
            Map.entry(35, new Integer[]{7, 136, 154}),
            Map.entry(36, new Integer[]{232, 32, 56}),
            Map.entry(37, new Integer[]{228, 233, 202}),
            Map.entry(38, new Integer[]{237, 0, 35}),
            Map.entry(39, new Integer[]{203, 233, 240})
    );

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

    public static Bitmap BWConvert(float[][][][] data) {
        int channels = data[0].length;
        assert channels == 1;
        int height = data[0][0].length;
        int width = data[0][0][0].length;
        int[] pixelData = new int[width * height];
        // only works on last layer rn
        for (float[][][] layer : data) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int g = (int) (layer[0][h][w] * 255.0);
                    pixelData[h * width + w] = Color.argb(255, g, g, g);
                }
            }
        }
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bm.setPixels(pixelData, 0, width, 0, 0, width, height);
        return bm;
    }

    public static Bitmap ColorConvert(float[][][][] data) {
        // TODO: Look into argmax for Java
        int channels = data[0].length;
        System.out.println("ONNX num channels (color): " + channels);
        int height = data[0][0].length;
        System.out.println("ONNX height (color): " + height);
        int width = data[0][0][0].length;
        System.out.println("ONNX width (color): " + width);
        System.out.println("ONNX num images (color): " + data.length);
        int[] pixelData = new int[width * height];
        // only works on last layer rn
        for (float[][][] layer : data) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int chosenChannel = 0;
                    float maxChannel = layer[0][h][w];
                    for (int c = 1; c < channels; c++) {
                        if (w == 125 && h == 125) {
                            System.out.println("ONNX Current Max Channel: " + maxChannel + " at " + chosenChannel);
                            System.out.println("ONNX This channel: " + layer[c][h][w] + " at " + c);
                        }
                        if (layer[c][h][w] > maxChannel) {
                            chosenChannel = c;
                            maxChannel = layer[c][h][w];
                        }
                    }
                    if (chosenChannel != 0) {
                        System.out.println("ONNX chosen channel for " + w + ", " + h + ": " + chosenChannel);
                    }
                    Integer[] color = colors.getOrDefault(chosenChannel, new Integer[]{255, 0, 0});
                    assert color != null;
                    pixelData[h * width + w] = Color.argb(255, color[0], color[1], color[2]);
                }
            }
        }

        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bm.setPixels(pixelData, 0, width, 0, 0, width, height);
        return bm;
    }
}
