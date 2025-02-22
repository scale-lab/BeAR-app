package com.example.arbenchapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.RunType;
import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.improvemodels.ImageConverter;
import com.example.arbenchapp.improvemodels.InputPreparation;
import com.example.arbenchapp.improvemodels.ParallelInference;
import com.example.arbenchapp.monitor.HardwareMonitor;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.*;

public class MTLBox {

    private final Settings settings;

    public MTLBox(Settings settings) {
        this.settings = settings;
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public MTLBoxStruct run(Bitmap bitmap, Context context) throws Exception {
        Bitmap default_bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", default_bm);
        Double default_tm = 0.0;
        HardwareMonitor.HardwareMetrics default_metrics = null;
        MTLBoxStruct default_mbs =
                new MTLBoxStruct(default_map, viewSettings().getRunType(), default_bm, default_tm, default_metrics);
        switch (this.settings.getRunType()) {
            case NONE:
                return default_mbs;
            case CONV2D:
                return runFromPt(bitmap, context, "gaussian_blur.pt");
            case DEEPLABV3:
                System.out.println("CONV2D RUN .. running deeplabv3");
                return runFromPt(bitmap, context, "deeplabv3.pt");
            case MTL:
                return runFromOnnx(bitmap, context, "resnet_seg_norm.onnx");
            default:
                return runFromPt(bitmap, context, "gaussian_blur.pt");
        }
    }

    public Bitmap TensorToBitmap(Tensor outputTensor, int width, int height) {
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

    public Bitmap TensorToBW(Tensor tensor, int width, int height) {
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

    public File getFile(Context context, String filename) {
        File file = new File(context.getFilesDir(), filename);
        if (true || !file.exists()) {
            try (InputStream is = context.getAssets().open(filename);
                 OutputStream os = Files.newOutputStream(file.toPath())) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    os.write(buf, 0, read);
                }
            } catch (Exception e) {
                System.err.println("Exception occurred with getFile: " + e.toString());
            }
        }
        return file;
    }

    public MTLBoxStruct runFromPt(Bitmap bitmap, Context context, String filename) throws Exception {
        File file = getFile(context, filename);
        System.out.println("CONV2D RUN .. file exists");

        Module model = Module.load(file.getPath());
        System.out.println("CONV2D RUN .. model loaded");

        //int batchSize = 16;
        // System.out.println("CONV2D .. input prep");
        // Tensor[] inputs = InputPreparation.prepareInputBatch(ImageConverter.bitmapToFloat2D(bitmap), batchSize);

        HardwareMonitor.PyTorchModelMonitor ptmm = new HardwareMonitor.PyTorchModelMonitor(model, context);
        HardwareMonitor.HardwareMetrics res = ptmm.executeAndMonitor(bitmap);
        System.out.println("CONV2D .. " + res.toString());

        Double mtm = res.executionTimeMs;
        Map<String, Tensor> out = res.output;
        if (out == null) {
            System.err.println("ERROR: Output map is null.");
            return null;
        }
        if (out.get("output") == null) {
            System.err.println("ERROR: Output map doesn't contain output field.");
            return null;
        }
        System.out.println("CONV2D .. able to convert stuff");
//        Bitmap bm = settings.getRunType() == RunType.DEEPLABV3 ?
//                TensorToBW(out.get("output"), bitmap.getWidth(), bitmap.getHeight()) :
//                TensorToBitmap(out.get("output"), bitmap.getWidth(), bitmap.getHeight());
        return new MTLBoxStruct(out, settings.getRunType(), bitmap, mtm, res, bitmap.getWidth(), bitmap.getHeight());
    }

    public MTLBoxStruct runFromOnnx(Bitmap bitmap, Context context, String filename) {
        System.out.println("ONNX: RUNFROMONNX START!");
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", bitmap);
        MTLBoxStruct mtlBoxStruct = new MTLBoxStruct(default_map, viewSettings().getRunType(), bitmap, 0.0, null);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            File file = getFile(context, filename);
            System.out.println("ONNX: filename: " + filename);
            OrtSession session = env.createSession(file.getPath(), new OrtSession.SessionOptions());
            if (!viewSettings().isDimsInit()) {
                System.err.println("ONNX: IMAGE DIMENSIONS NOT SPECIFIED IN SETTINGS!");
                return mtlBoxStruct;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    viewSettings().getImgWidth(),
                    viewSettings().getImgHeight(),
                    false
            );
            HardwareMonitor.PyTorchModelMonitor omm = new HardwareMonitor.PyTorchModelMonitor(session, env, context);
            HardwareMonitor.HardwareMetrics res = omm.executeAndMonitor(scaled);
            return new MTLBoxStruct(res.output, viewSettings().getRunType(), scaled, res.executionTimeMs, res, scaled.getWidth(), scaled.getHeight());
        } catch (OrtException ortException) {
            System.err.println("ONNX: EXCEPTION WHEN CREATING OR USING OrtSession: "
                    + ortException.getMessage());
        }
        return mtlBoxStruct;
    }

}
