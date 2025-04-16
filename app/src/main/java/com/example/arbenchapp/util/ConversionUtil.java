package com.example.arbenchapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;

import com.example.arbenchapp.datatypes.ConversionMethod;

import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

public final class ConversionUtil {
    private ConversionUtil() {}

    public static OnnxTensor bitmapToTensor(Bitmap bitmap, OrtEnvironment env) throws OrtException {
        // Only convert bitmap format if necessary
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channels = 3;
        int batchSize = 1;
        int totalPixels = width * height;

        // Precompute background color components
        final int bgColor = Color.WHITE;
        final int bgRed = (bgColor >> 16) & 0xFF;
        final int bgGreen = (bgColor >> 8) & 0xFF;
        final int bgBlue = bgColor & 0xFF;

        // Prepare float buffer directly
        FloatBuffer floatBuffer = ByteBuffer.allocateDirect(batchSize * channels * width * height * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        // Get pixel data
        int[] intValues = new int[totalPixels];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        // Process pixels directly in memory
        final float scale = 1.0f / 255.0f;
        for (int i = 0; i < totalPixels; i++) {
            int pixel = intValues[i];
            int alpha = (pixel >> 24) & 0xFF;

            // Extract or blend colors
            float red, green, blue;
            if (alpha == 255) {
                red = ((pixel >> 16) & 0xFF) * scale;
                green = ((pixel >> 8) & 0xFF) * scale;
                blue = (pixel & 0xFF) * scale;
            } else {
                // Alpha blending with background
                float alphaRatio = alpha * scale;
                float inverseAlphaRatio = 1.0f - alphaRatio;

                int pixelRed = (pixel >> 16) & 0xFF;
                int pixelGreen = (pixel >> 8) & 0xFF;
                int pixelBlue = pixel & 0xFF;

                red = (pixelRed * alphaRatio + bgRed * inverseAlphaRatio) * scale;
                green = (pixelGreen * alphaRatio + bgGreen * inverseAlphaRatio) * scale;
                blue = (pixelBlue * alphaRatio + bgBlue * inverseAlphaRatio) * scale;
            }

            // Write directly to float buffer positions
            floatBuffer.put(i, red);
            floatBuffer.put(i + totalPixels, green);
            floatBuffer.put(i + 2 * totalPixels, blue);
        }

        long[] shape = {batchSize, channels, height, width};
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
                try {
                    return ImageConversionUtil.ColorConvert(data);
                } catch (InterruptedException e) {
                    throw new RuntimeException(
                            "TIMEEC ERROR: " + e
                    );
                }
            case BW:
                return ImageConversionUtil.BWConvert(data);
            case COLOR_GRADIENT:
                try {
                    return ImageConversionUtil.ColorGradientConvert(data);
                } catch (InterruptedException e) {
                    throw new RuntimeException(
                            "TIMEEC ERROR: " + e
                    );
                }
            case BW_GRADIENT:
                return ImageConversionUtil.BWGradientConvert(data);
            default:
                throw new IllegalArgumentException(
                        "ERROR: Conversion method " + method.toString() + " provided, not valid for float array input.");
        }
    }

    public static ConversionMethod stringToConversionMethod(String method) {
        System.out.println("ONNX conversion string: " + method);
        switch (method) {
            case "BLACK AND WHITE":
                return ConversionMethod.BW;
            case "COLOR":
                return ConversionMethod.COLOR;
            case "ARGMAX COLOR":
                return ConversionMethod.ARGMAX_COLOR;
            case "B&W GRADIENT":
                return ConversionMethod.BW_GRADIENT;
            case "COLOR GRADIENT":
                return ConversionMethod.COLOR_GRADIENT;
            default:
                return ConversionMethod.DEFAULT;
        }
    }

    public static String round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(places);
        return df.format(value);
    }

    public static String byteString(double value, int places) {
        String unit;
        if (value > 1_000_000_000) {
            unit = " GB";
            value /= 1_000_000_000;
        } else if (value > 1_000_000) {
            unit = " MB";
            value /= 1_000_000;
        } else {
            unit = " bytes";
        }
        return round(value, places) + unit;
    }

    public static void logArray(Context context, String[] data, String filename) {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            System.err.println("ONNX external storage not writable!");
            return;
        }
        File externalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BeAR_Logs");
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        File file = new File(externalDir, filename);
        FileOutputStream fos = null;
        try {
            String content = android.text.TextUtils.join("\n", data);
            fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.flush();
            System.out.println("ONNX file write successful!");
        } catch (IOException e) {
            System.err.println("ONNX error with file: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    System.err.println("ONNX error with file: " + e.getMessage());
                }
            }
        }
    }
}
