package com.example.arbenchapp.improvemodels;

import android.graphics.Bitmap;
import org.pytorch.Tensor;
import java.nio.FloatBuffer;
import java.util.concurrent.Future;

public class ImageConverter {
    public static float[][] bitmapToFloat2D(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float[][] result = new float[3][width * height];

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            // Normalize to [0,1] range and apply standard ImageNet normalization
            result[0][i] = ((((pixel >> 16) & 0xFF) / 255.0f) - 0.485f) / 0.229f; // R
            result[1][i] = ((((pixel >> 8) & 0xFF) / 255.0f) - 0.456f) / 0.224f;  // G
            result[2][i] = (((pixel & 0xFF) / 255.0f) - 0.406f) / 0.225f;         // B
        }
        return result;
    }

    public static Tensor combineTensorFutures(Future<Tensor>[] futures) throws Exception {
        int batchSize = futures.length;

        // Get first tensor to determine dimensions
        Tensor firstTensor = futures[0].get();
        long[] shape = firstTensor.shape();
        int tensorSize = (int) java.util.Arrays.stream(shape).skip(1).reduce(1, (a, b) -> a * b);

        // Allocate buffer for combined tensor
        FloatBuffer buffer = Tensor.allocateFloatBuffer(batchSize * tensorSize);

        // Combine all tensors
        for (int i = 0; i < batchSize; i++) {
            float[] data = futures[i].get().getDataAsFloatArray();
            buffer.put(data);
        }
        buffer.rewind();

        // Create new shape with batch dimension
        long[] newShape = new long[shape.length];
        newShape[0] = batchSize;
        System.arraycopy(shape, 1, newShape, 1, shape.length - 1);

        return Tensor.fromBlob(buffer, newShape);
    }

    public static float[][] tensorToFloat2D(Tensor tensor) {
        float[] flatData = tensor.getDataAsFloatArray();
        long[] shape = tensor.shape();
        int channels = (int) shape[1];
        int size = flatData.length / channels;

        float[][] result = new float[channels][size];
        for (int c = 0; c < channels; c++) {
            System.arraycopy(flatData, c * size, result[c], 0, size);
        }
        return result;
    }

    public static Tensor float2DToTensor(float[][] data) {
        int channels = data.length;
        int size = data[0].length;
        FloatBuffer buffer = Tensor.allocateFloatBuffer(channels * size);

        for (float[] channel : data) {
            buffer.put(channel);
        }
        buffer.rewind();

        return Tensor.fromBlob(buffer, new long[]{1, channels, size});
    }
}
