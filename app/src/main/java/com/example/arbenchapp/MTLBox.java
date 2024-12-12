package com.example.arbenchapp;

import android.graphics.Bitmap;

import com.example.arbenchapp.datatypes.Settings;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

public class MTLBox {

    private final Settings settings;

    public MTLBox(Settings settings) {
        this.settings = settings;
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public Bitmap run(Bitmap bitmap) {
        System.out.println("HELLO RUN!");
        Bitmap default_bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        switch (this.settings.getRunType()) {
            case NONE:
                return default_bm;
            case CONV2D:
                return conv2d(bitmap);
            case TORCH:
                return default_bm;
            default:
                return conv2d(bitmap);
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

    private Bitmap TensorToBitmap(Tensor tensor, int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        TensorImageUtils.bitmapToFloat32Tensor(bitmap, 0, 0, w, h,
                new float[] {0.0f, 0.0f, 0.0f}, // Mean RGB values for normalization
                new float[] {1.0f, 1.0f, 1.0f}); // Standard deviation for normalization
        return bitmap;
    }

    public Bitmap conv2d(Bitmap bitmap) {
        System.out.println("CONV2D RUN!!");
        Module blur_model = Module.load("com/example/arbenchapp/pretrained/gaussian_blur.pt");
        Tensor inp = BitmapToTensor(bitmap);
        Tensor out = blur_model.forward(IValue.from(inp)).toTensor();
        return TensorToBitmap(out, bitmap.getWidth(), bitmap.getHeight());
    }

}
