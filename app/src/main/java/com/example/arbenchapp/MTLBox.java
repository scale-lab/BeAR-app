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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
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
            case SEG_NORM_MTL:
                return runFromOnnx(bitmap, context, "resnet_seg_norm.onnx");
            case SWIN_MTL:
                return runFromOnnx(bitmap, context, "swin_mtl_nyud.onnx");
            default:
                return runFromPt(bitmap, context, "gaussian_blur.pt");
        }
    }

    public String computeSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        fis.close();
        byte[] hash = digest.digest();

        // Convert hash bytes to hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
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

        Module model = Module.load(file.getPath());

        HardwareMonitor.PyTorchModelMonitor ptmm =
                new HardwareMonitor.PyTorchModelMonitor(model, viewSettings(), context);
        HardwareMonitor.HardwareMetrics res = ptmm.executeAndMonitor(bitmap);

        Double mtm = res.executionTimeMs;
        Map<String, Bitmap> out = res.output;
        if (out == null) {
            System.err.println("ERROR: Output map is null.");
            return null;
        }
        if (out.get("output") == null) {
            System.err.println("ERROR: Output map doesn't contain output field.");
            return null;
        }
        return new MTLBoxStruct(out, settings.getRunType(), bitmap, mtm, res);
    }

    public MTLBoxStruct runFromOnnx(Bitmap bitmap, Context context, String filename) {
        Thread currentThread = Thread.currentThread();
        System.out.println("ONNX: RUNFROMONNX START! " + currentThread.getName() + ", " + currentThread.getId());
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", bitmap);
        MTLBoxStruct mtlBoxStruct = new MTLBoxStruct(default_map, viewSettings().getRunType(), bitmap, 0.0, null);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            File file = getFile(context, filename);
            try {
                System.out.println("ONNX file hash: " + computeSHA256(file));
            } catch (Exception e) {
                System.out.println("ONNX EXCEPTION WITH FILE HASH: " + e);
            }
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
            HardwareMonitor.PyTorchModelMonitor omm =
                    new HardwareMonitor.PyTorchModelMonitor(session, env, viewSettings(), context);
            HardwareMonitor.HardwareMetrics res = omm.executeAndMonitor(scaled);
            return new MTLBoxStruct(res.output, viewSettings().getRunType(), scaled, res.executionTimeMs, res);
        } catch (OrtException ortException) {
            System.err.println("ONNX: EXCEPTION WHEN CREATING OR USING OrtSession: "
                    + ortException.getMessage());
        }
        return mtlBoxStruct;
    }

}
