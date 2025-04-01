package com.example.arbenchapp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.util.Log;

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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import ai.onnxruntime.*;
import ai.onnxruntime.providers.NNAPIFlags;

public class MTLBox {

    private final Settings settings;
    private final File file;
    private final SharedPreferences prefs;
    private HardwareMonitor.PyTorchModelMonitor monitor;
    private HardwareMonitor.HardwareMetrics metrics;
    private final ModelType modelType;
    private final MTLBoxStruct default_mbs;
    private final int secondsBetweenMetrics;
    private final int framesBetweenMetrics;
    private Context context;
    private boolean nnapi_compat;
    private boolean nnapi;
    private OrtSession nn_sesh;
    private OrtSession cpu_sesh;

    public MTLBox(Settings settings, Context context) {
        this.settings = settings;
        this.context = context;
        this.nnapi = true;
        this.nnapi_compat = true;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
            EnumSet<OrtProvider> providers = OrtEnvironment.getAvailableProviders();
            for (OrtProvider provider : providers) {
                Log.d("ORT_1298", "Available provider: " + provider); // Should show "NNAPI", "OPENCL", etc.
            }
            try {
                OrtSession session = createNNSession(env);
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
            Intent batteryIntent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            assert batteryIntent != null;
            double temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0;
            double tempThresh = 35.0;
            if ((nnapi && temp > tempThresh) || (!nnapi && temp < tempThresh)) {
                updateSession();
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
        if (monitor.getCurrentTime() > secondsBetweenMetrics ||
                monitor.getNumFrames() >= framesBetweenMetrics ||
                !prefs.getBoolean("use_camera", false)) {
            HardwareMonitor.HardwareMetrics hardwareMetrics = monitor.finishExecuteAndMonitor();
            return new MTLBoxStruct(output, bitmap, hardwareMetrics.executionTimeMs, hardwareMetrics);
        }
        return new MTLBoxStruct(output, bitmap, (System.nanoTime() - startTime) / 1_000_000);
    }

    private OrtSession createNNSession(OrtEnvironment env) throws OrtException {
        if (nn_sesh != null) {
            return nn_sesh;
        }
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        EnumSet<NNAPIFlags> nnapiFlags = EnumSet.of(
                NNAPIFlags.CPU_DISABLED,
                NNAPIFlags.USE_FP16
        );
        options.addNnapi(nnapiFlags);
        try {
            nn_sesh = env.createSession(file.getPath(), options);
            return nn_sesh;
        } catch (OrtException e) {
            this.nnapi_compat = false;
            return createSession(env);
        }
    }

    private OrtSession createSession(OrtEnvironment env) throws OrtException {
        if (cpu_sesh != null) {
            return cpu_sesh;
        }
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        cpu_sesh = env.createSession(file.getPath(), options);
        return cpu_sesh;
    }

    private void updateSession() throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session;
        if (!this.nnapi && this.nnapi_compat) {
            // switch to gpu and npu
            session = createNNSession(env);
        } else {
            // switch to cpu
            session = createSession(env);
        }
        this.nnapi = !this.nnapi;
        this.monitor.updateSession(session);
    }

    public void shutdown() throws OrtException {
        nn_sesh.close();
        cpu_sesh.close();
    }

}
