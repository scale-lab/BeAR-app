package com.example.arbenchapp.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.arbenchapp.datatypes.ConversionMethod;
import com.example.arbenchapp.datatypes.RunType;

import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

public final class ConversionUtil {
    private ConversionUtil() {}

    public static OnnxTensor bitmapToTensor(Bitmap bitmap, OrtEnvironment env) throws OrtException {
        // Ensure bitmap is in ARGB_8888 format (to check alpha)
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int bgColor = Color.WHITE;

        // Create a new bitmap with the background color
        Bitmap processedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(processedBitmap);
        canvas.drawColor(bgColor); // Set background color
        canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG)); // Draw image on top

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channels = 3;
        int batchSize = 1;

        FloatBuffer floatBuffer = ByteBuffer.allocateDirect(batchSize * channels * width * height * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        // Convert bitmap pixels to a float array
        float[] floatValues = new float[width * height * channels];
        int totalPixels = width * height;
        int[] intValues = new int[totalPixels];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        for (int i = 0; i < totalPixels; i++) {
            int pixel = intValues[i];
            floatValues[i] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
            floatValues[i + totalPixels] = ((pixel >> 8) & 0xFF) / 255.0f; // Green
            floatValues[i + (2 * totalPixels)] = (pixel & 0xFF) / 255.0f; // Blue
        }

        // Convert to FloatBuffer
        floatBuffer.put(floatValues);
        floatBuffer.rewind();
        long[] shape = {batchSize, channels, height, width}; // NCHW format

        // Create an OnnxTensor
        return OnnxTensor.createTensor(env, floatBuffer, shape);
    }

    public static Tensor bitmapToTensor(Bitmap bitmap) {
        // Normalize the image using ImageNet mean and standard deviation
        float[] normMeanRGB = {0.485f, 0.456f, 0.406f};
        float[] normStdRGB = {0.229f, 0.224f, 0.225f};

        // Convert the bitmap to a float32 tensor
        return TensorImageUtils.bitmapToFloat32Tensor(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), normMeanRGB, normStdRGB
        );
    }

    public static Bitmap TensorToImage(Tensor tensor, ConversionMethod method, int width, int height) {
        switch (method) {
            case COLOR:
                return ImageConversionUtil.ColorConvert(tensor, width, height);
            case BW:
                return ImageConversionUtil.BWConvert(tensor, width, height);
            default:
                throw new IllegalArgumentException(
                        "ERROR: Conversion method " + method.toString() + " provided, not valid for tensor input.");
        }
    }

    public static Bitmap FloatArrayToImage(float[][][][] data, ConversionMethod method) {
        switch (method) {
            case ARGMAX_COLOR:
                return ImageConversionUtil.ColorConvert(data);
            case BW:
                return ImageConversionUtil.BWConvert(data);
            default:
                throw new IllegalArgumentException(
                        "ERROR: Conversion method " + method.toString() + " provided, not valid for float array input.");
        }
    }

    public static Map<String, ConversionMethod> getConversionMap(RunType type) {
        switch (type) {
            case CONV2D:
                return Map.of("output", ConversionMethod.DEFAULT);
            case DEEPLABV3:
                return Map.of("output", ConversionMethod.BW);
            case SEG_NORM_MTL:
                return Map.of("semseg_out", ConversionMethod.ARGMAX_COLOR,
                        "normals_out", ConversionMethod.ARGMAX_COLOR);
            case SWIN_MTL:
                return Map.of("semseg", ConversionMethod.ARGMAX_COLOR,
                        "depth", ConversionMethod.BW);
            default:
                return Map.of();
        }
    }
}
