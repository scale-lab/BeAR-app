package com.example.arbenchapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import com.example.arbenchapp.datatypes.postprocessing.ConversionMethod;

import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

public final class ConversionUtil {
    private ConversionUtil() {}

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
