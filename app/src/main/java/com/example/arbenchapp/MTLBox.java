package com.example.arbenchapp;

import android.content.Context;
import android.graphics.Bitmap;

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
        Double default_tm = 0.0;
        HardwareMonitor.HardwareMetrics default_metrics = null;
        MTLBoxStruct default_mbs = new MTLBoxStruct(default_bm, default_tm, default_metrics);
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
        if (!file.exists()) {
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
        Tensor inp = BitmapToTensor(bitmap);
        // System.out.println("CONV2D .. input prep");
        // Tensor[] inputs = InputPreparation.prepareInputBatch(ImageConverter.bitmapToFloat2D(bitmap), batchSize);

        HardwareMonitor.PyTorchModelMonitor ptmm = new HardwareMonitor.PyTorchModelMonitor(model, context);
        HardwareMonitor.HardwareMetrics res = ptmm.executeAndMonitor(IValue.from(inp));
        System.out.println("CONV2D .. " + res.toString());

        Double mtm = res.executionTimeMs;
        Tensor out = res.output;
        System.out.println("CONV2D .. able to convert stuff");
        Bitmap bm = out != null ? (settings.getRunType() == RunType.DEEPLABV3 ?
                TensorToBW(out, bitmap.getWidth(), bitmap.getHeight()) :
                TensorToBitmap(out, bitmap.getWidth(), bitmap.getHeight()))
                : null;
        System.out.println("CONV2D .. made bitmap, is null: " + (bitmap == null));
        return new MTLBoxStruct(bm, mtm, res);
    }

    public static OnnxTensor bitmapToTensor(Bitmap bitmap, OrtEnvironment env) throws OrtException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channels = bitmap.hasAlpha() ? 4 : 3;

        // Convert bitmap pixels to a float array
        float[] floatValues = new float[width * height * channels];
        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);

        for (int i = 0; i < intValues.length; i++) {
            int pixel = intValues[i];
            floatValues[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
            floatValues[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f; // Green
            floatValues[i * 3 + 2] = (pixel & 0xFF) / 255.0f; // Blue
        }

        // Convert to FloatBuffer
        FloatBuffer buffer = FloatBuffer.wrap(floatValues);
        long[] shape = {1, channels, height, width}; // NCHW format

        // Create an OnnxTensor
        return OnnxTensor.createTensor(env, buffer, shape);
    }

    public MTLBoxStruct runFromOnnx(Bitmap bitmap, Context context, String filename) {
        System.out.println("ONNX: RUNFROMONNX START!");
        MTLBoxStruct mtlBoxStruct = new MTLBoxStruct(bitmap, 0.0, null);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            File file = getFile(context, filename);
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
            OnnxTensor input = bitmapToTensor(scaled, env);
            Map<String, ? extends OnnxTensorLike> inputs = Map.of("input", input);
            OrtSession.Result outputs = session.run(inputs);
            System.out.println("ONNX: OUTPUTS SIZE: " + outputs.size());
            outputs.forEach(k -> System.out.println("ONNX: key: " + k.toString()));
            for (Map.Entry<String, OnnxValue> entry : outputs) {
                String key = entry.getKey();
                OnnxValue value = entry.getValue();
                if (value.getType() == OnnxValue.OnnxValueType.ONNX_TYPE_TENSOR) {
                    float[][][] ft = (float[][][]) value.getValue();
                } else {
                    System.err.println("ONNX: VALUE ISN'T TENSOR.");
                    return mtlBoxStruct;
                }
            }
        } catch (OrtException ortException) {
            System.err.println("ONNX: EXCEPTION WHEN CREATING OR USING OrtSession: "
                    + ortException.getMessage());
        }
        return mtlBoxStruct;
    }

}
