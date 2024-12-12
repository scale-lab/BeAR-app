package com.example.arbenchapp;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.arbenchapp.datatypes.Settings;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class MTLBox {

    private final Settings settings;

    public MTLBox(Settings settings) {
        this.settings = settings;
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public Bitmap run(Bitmap bitmap, Context context) {
        Bitmap default_bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        switch (this.settings.getRunType()) {
            case NONE:
                return default_bm;
            case CONV2D:
                return conv2d(bitmap, context, "gaussian_blur.pt");
            case TORCH:
                return default_bm;
            default:
                return conv2d(bitmap, context, "gaussian_blur.pt");
        }
    }

    private Tensor BitmapToTensor(Bitmap bitmap) {
        // Normalize the image using ImageNet mean and standard deviation
        float[] normMeanRGB = {0.485f, 0.456f, 0.406f};
        float[] normStdRGB = {0.229f, 0.224f, 0.225f};

        // Convert the bitmap to a float32 tensor
        return TensorImageUtils.bitmapToFloat32Tensor(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), normMeanRGB, normStdRGB
        );
    }

    private Bitmap TensorToBitmap(Tensor outputTensor, int width, int height) {
        try {
            // Get tensor data as float array
            float[] tensorValues = outputTensor.getDataAsFloatArray();

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

    public Bitmap conv2d(Bitmap bitmap, Context context, String filename) {
        File blur_file = new File(context.getFilesDir(), filename);
        if (!blur_file.exists()) {
            try (InputStream is = context.getAssets().open(filename);
                 OutputStream os = Files.newOutputStream(blur_file.toPath())) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    os.write(buf, 0, read);
                }
            } catch (Exception e) {
                System.err.println("Exception occurred with conv2d run: " + e.toString());
            }
        }
        Module blur_model = Module.load(blur_file.getPath());
        Tensor inp = BitmapToTensor(bitmap);
        Tensor out = blur_model.forward(IValue.from(inp)).toTensor();
        return TensorToBitmap(out, bitmap.getWidth(), bitmap.getHeight());
    }

}
