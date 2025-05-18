package com.example.arbenchapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.example.arbenchapp.datatypes.preprocessing.Resolution;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraUtil {
    private static final String TAG = "CameraXUtil";
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private final Context context;
    private final CameraCallback callback;

    public interface CameraCallback {
        void onFrameCaptured(Bitmap bitmap);
    }

    public CameraUtil(Context context, CameraCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start CameraX", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Resolution res = new Resolution(prefs.getString("resolution", "224,224"));

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(res.getWidth(), res.getHeight()))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        final boolean[] processing = { false };

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            if (!processing[0]) {
                processing[0] = true;
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    callback.onFrameCaptured(bitmap);
                }
                processing[0] = false;
            }
            image.close();
        });

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((androidx.lifecycle.LifecycleOwner) context, cameraSelector, imageAnalysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageToBitmap(ImageProxy image) {
        Image img = image.getImage();
        if (img == null) return null;

        Bitmap bitmap;

        if (img.getFormat() == ImageFormat.YUV_420_888) {
            YuvImage yuvImage = getYuvImage(image, img);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
            byte[] imageBytes = out.toByteArray();
            bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } else {
            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }

        int rotation = image.getImageInfo().getRotationDegrees();

        if (rotation != 0) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(rotation);

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);

            if (bitmap != rotatedBitmap) {
                bitmap.recycle();
            }

            bitmap = rotatedBitmap;
        }

        return bitmap;
    }

    private static @NonNull YuvImage getYuvImage(ImageProxy image, Image img) {
        Image.Plane[] planes = img.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
    }

    public void shutdown() {
        cameraExecutor.shutdown();
    }
}
