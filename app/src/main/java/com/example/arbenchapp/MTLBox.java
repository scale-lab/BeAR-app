package com.example.arbenchapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.preference.PreferenceManager;

import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.ModelType;
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
    private final File file;
    private HardwareMonitor.PyTorchModelMonitor monitor;
    private HardwareMonitor.HardwareMetrics metrics;
    private final ModelType modelType;
    private final MTLBoxStruct default_mbs;
    private final int secondsBetweenMetrics;
    private final int framesBetweenMetrics;
    private final Context context;

    public MTLBox(Settings settings, Context context) {
        this.settings = settings;
        this.context = context;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean splitModel = prefs.getBoolean("split_inference", false);
        if (splitModel) {
            // EXPERIMENTAL!!

        }

        String modelPath = prefs.getString("model_file_selection", "");
        secondsBetweenMetrics = Integer.parseInt(prefs.getString("update_freq_time", "5"));
        framesBetweenMetrics = Integer.parseInt(prefs.getString("update_freq_frames", "1"));
        this.file = getFile(context, modelPath);
        if (modelPath.length() < 4) {
            System.err.println("NO MODEL SELECTED!");
            this.modelType = ModelType.ERROR;
            this.monitor = null;
            this.metrics = null;
        } else if (modelPath.endsWith(".pt")) {
            this.modelType = ModelType.PT;
            this.monitor = new HardwareMonitor.PyTorchModelMonitor(
                    Module.load(file.getPath()), viewSettings(), context);
            this.metrics = null;
        } else if (modelPath.endsWith(".onnx")) {
            this.modelType = ModelType.ONNX;
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            try {
                OrtSession session = env.createSession(file.getPath(), new OrtSession.SessionOptions());
                this.monitor = new HardwareMonitor.PyTorchModelMonitor(session, env, viewSettings(), context);
                this.metrics = new HardwareMonitor.HardwareMetrics(context);
            } catch (OrtException e) {
                System.err.println("ORTEXCEPTION: " + e);
                this.monitor = null;
                this.metrics = null;
            }
        } else {
            System.err.println("INVALID MODEL TYPE, MUST BE .pt OR .onnx");
            this.modelType = ModelType.ERROR;
            this.monitor = null;
            this.metrics = null;
        }
        Bitmap default_bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", default_bm);
        Double default_tm = 0.0;
        default_mbs = new MTLBoxStruct(default_map, default_bm, default_tm, null);
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public MTLBoxStruct run(Bitmap bitmap) throws Exception {
        switch (modelType) {
            case PT:
                return runFromPt(bitmap);
            case ONNX:
                return runFromOnnx(bitmap);
            case ERROR:
                System.err.println("PREVIOUS ERROR IN DETERMINING MODEL TYPE");
                return default_mbs;
            default:
                System.err.println("UNKNOWN MODEL TYPE");
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

    private MTLBoxStruct runFromPt(Bitmap bitmap) throws Exception {
        HardwareMonitor.HardwareMetrics res = monitor.executeAndMonitor(bitmap);
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

    private MTLBoxStruct runFromOnnx(Bitmap bitmap) {
        if (!monitor.hasStarted()) {
            monitor.startExecuteAndMonitor();
        }
        double startTime = System.nanoTime();
        Map<String, Bitmap> output;
        try {
            if (!viewSettings().isDimsInit()) {
                System.err.println("ONNX: IMAGE DIMENSIONS NOT SPECIFIED IN SETTINGS!");
                return default_mbs;
            }
            if (bitmap.getWidth() != viewSettings().getImgWidth() ||
                bitmap.getHeight() != viewSettings().getImgHeight()) {
                Bitmap scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        viewSettings().getImgWidth(),
                        viewSettings().getImgHeight(),
                        true
                );
                output = monitor.runInference(scaled);
            } else {
                output = monitor.runInference(bitmap);
            }
        } catch (OrtException e) {
            System.err.println("ONNX ORTEXCEPTION: " + e.getMessage());
            return default_mbs;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (monitor.getCurrentTime() > secondsBetweenMetrics ||
                monitor.getNumFrames() >= framesBetweenMetrics ||
                !prefs.getBoolean("use_camera", false)) {
            HardwareMonitor.HardwareMetrics hardwareMetrics = monitor.finishExecuteAndMonitor();
            return new MTLBoxStruct(output, bitmap, hardwareMetrics.executionTimeMs, hardwareMetrics);
        }
        return new MTLBoxStruct(output, bitmap, (System.nanoTime() - startTime) / 1_000_000);
    }

}
