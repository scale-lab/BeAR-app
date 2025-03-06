package com.example.arbenchapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.monitor.HardwareMonitor;

import org.pytorch.Module;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
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
        MTLBoxStruct default_mbs =
                new MTLBoxStruct(default_map, default_bm, default_tm, null);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String modelPath = prefs.getString("model_file_selection", "");
        if (modelPath.length() < 4) {
            System.err.println("NO MODEL SELECTED!");
            return default_mbs;
        } else if (modelPath.endsWith(".pt")) {
            return runFromPt(bitmap, context, modelPath);
        } else if (modelPath.endsWith(".onnx")) {
            return runFromOnnx(bitmap, context, modelPath);
        } else {
            System.err.println("INVALID MODEL TYPE, MUST BE .pt OR .onnx");
            return default_mbs;
        }
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

    private MTLBoxStruct runFromPt(Bitmap bitmap, Context context, String filename) throws Exception {
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
        return new MTLBoxStruct(out, bitmap, mtm, res);
    }

    private MTLBoxStruct runFromOnnx(Bitmap bitmap, Context context, String filename) {
        Thread currentThread = Thread.currentThread();
        System.out.println("ONNX: RUNFROMONNX START! " + currentThread.getName() + ", " + currentThread.getId());
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", bitmap);
        MTLBoxStruct mtlBoxStruct = new MTLBoxStruct(default_map, bitmap, 0.0, null);
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
                    true
            );
            HardwareMonitor.PyTorchModelMonitor omm =
                    new HardwareMonitor.PyTorchModelMonitor(session, env, viewSettings(), context);
            HardwareMonitor.HardwareMetrics res = omm.executeAndMonitor(scaled);
            return new MTLBoxStruct(res.output, scaled, res.executionTimeMs, res);
        } catch (OrtException ortException) {
            System.err.println("ONNX: EXCEPTION WHEN CREATING OR USING OrtSession: "
                    + ortException.getMessage());
        }
        return mtlBoxStruct;
    }

}
