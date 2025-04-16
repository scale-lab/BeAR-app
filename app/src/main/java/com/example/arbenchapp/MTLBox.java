package com.example.arbenchapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import androidx.preference.PreferenceManager;

import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.ModelType;
import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.datatypes.SplitInfo;
import com.example.arbenchapp.monitor.HardwareMonitor;
import com.example.arbenchapp.util.ConversionUtil;

import org.pytorch.Module;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.*;
import ai.onnxruntime.providers.NNAPIFlags;

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
    private SplitInfo splitInfo;
    private final String inputName;

    public MTLBox(Settings settings, Context context) {
        this.settings = settings;
        this.context = context;

        Bitmap default_bm = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Map<String, Bitmap> default_map = new HashMap<>();
        default_map.put("output", default_bm);
        Double default_tm = 0.0;
        default_mbs = new MTLBoxStruct(default_map, default_bm, default_tm, null);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        secondsBetweenMetrics = Integer.parseInt(prefs.getString("update_freq_time", "5"));
        framesBetweenMetrics = Integer.parseInt(prefs.getString("update_freq_frames", "1"));
        boolean splitModel = prefs.getBoolean("split_inference", false);
        String encoderName = prefs.getString("encoder_selection", "");
        Set<String> decoderNames = prefs.getStringSet("decoder_selection", new HashSet<>());

        if (splitModel && !encoderName.isEmpty() && !decoderNames.isEmpty()) {
            // EXPERIMENTAL!!
            this.modelType = ModelType.SPLIT;
            this.file = null;
            this.monitor = null;
            this.inputName = "pixel_values";
            if (!encoderName.endsWith(".onnx")) {
                encoderName += ".onnx";
            }
            File encoderFile = getFile(context, encoderName, "encoders/");
            File[] decoderFiles = new File[decoderNames.size()];
            int pos = 0;
            for (String decoderName : decoderNames) {
                char slash = '/';
                int index = decoderName.indexOf(slash);
                String splitDecoderName = decoderName;
                if (index != -1) {
                    splitDecoderName = splitDecoderName.substring(index + 1);
                }
                if (!decoderName.endsWith(".onnx")) {
                    splitDecoderName += ".onnx";
                }
                decoderFiles[pos] = getFile(context, splitDecoderName, "decoders/");
                System.out.println("ORTEXCEPTION decoder " + pos + ": " + decoderFiles[pos].getPath());
                pos++;
            }
            System.out.println("ORTEXCEPTION encoder: " + encoderFile.getPath());
            System.out.println("ORTEXCEPTION decoders: " + Arrays.toString(decoderFiles));
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            try {
                // TODO: Make sure encoder is on NPU and decoder is on CPU/multithreaded
                // TODO: Get code from java_benchmark branch
                // warm up conversion util
                ConversionUtil.bitmapToTensor(default_bm, env);
                // create sessions
                OrtSession encoderSession = createNNSession(encoderFile, env);
                OrtSession[] decoderSessions = new OrtSession[decoderFiles.length];
                for (int i = 0; i < decoderFiles.length; i++) {
                    System.out.println("ORTEXCEPTION decoder at " + i + ": " + decoderFiles[i].getPath());
                    decoderSessions[i] = createSession(decoderFiles[i], env);
                }
                HardwareMonitor.SplitModelMonitor splitModelMonitor = new HardwareMonitor.SplitModelMonitor(
                        encoderSession,
                        decoderSessions,
                        env,
                        viewSettings(),
                        inputName,
                        context
                );
                this.splitInfo = new SplitInfo(encoderFile, decoderFiles, splitModelMonitor);
                this.metrics = new HardwareMonitor.HardwareMetrics(context);
            } catch (OrtException e) {
                System.err.println("ORTEXCEPTION: " + e);
                this.splitInfo = null;
                this.metrics = null;
            }

            return;
        }

        String modelPath = prefs.getString("model_file_selection", "");

        String prefix = "full/";
        this.file = getFile(context, modelPath, prefix);
        this.splitInfo = null;
        this.inputName = "input";
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
                // warm up conversion util
                ConversionUtil.bitmapToTensor(default_bm, env);
                // create session
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
    }

    public Settings viewSettings() {
        return this.settings;
    }

    public MTLBoxStruct run(Bitmap bitmap) {
        switch (modelType) {
            case PT:
                return runFromPt(bitmap);
            case ONNX:
                return runFromOnnx(bitmap);
            case SPLIT:
                return runSplit(bitmap);
            case ERROR:
                System.err.println("PREVIOUS ERROR IN DETERMINING MODEL TYPE");
                return default_mbs;
            default:
                System.err.println("UNKNOWN MODEL TYPE");
                return default_mbs;
        }
    }

    public File getFile(Context context, String filename, String prefix) {
        System.out.println("FILFELNAME: " + context.getFilesDir());
        File file = new File(context.getFilesDir(), filename);
        if (!file.exists()) {
            System.out.println("FILFELNAME prefix + filename: " + prefix + filename);
            try (InputStream is = context.getAssets().open(prefix + filename);
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

    private MTLBoxStruct runFromPt(Bitmap bitmap) {
        try {
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
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            return null;
        }
    }

    private MTLBoxStruct runFromOnnx(Bitmap bitmap) {
        if (monitor == null) {
            return default_mbs;
        }
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

    private MTLBoxStruct runSplit(Bitmap bitmap) {
        System.out.println("WADSGOIJ runSplit");
        if (splitInfo == null) {
            return default_mbs;
        }
        if (splitInfo.getMonitor() == null) {
            return default_mbs;
        }
        if (!splitInfo.getMonitor().hasStarted()) {
            splitInfo.getMonitor().startExecuteAndMonitor();
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
                output = splitInfo.getMonitor().run(scaled);
            } else {
                output = splitInfo.getMonitor().run(bitmap);
            }
        } catch (OrtException e) {
            System.err.println("ONNX ORTEXCEPTION: " + e.getMessage());
            return default_mbs;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (splitInfo.getMonitor().getCurrentTime() > secondsBetweenMetrics ||
                splitInfo.getMonitor().getNumFrames() >= framesBetweenMetrics ||
                !prefs.getBoolean("use_camera", false)) {
            HardwareMonitor.HardwareMetrics hardwareMetrics = splitInfo.getMonitor().finishExecuteAndMonitor();
            return new MTLBoxStruct(output, bitmap, hardwareMetrics.executionTimeMs, hardwareMetrics);
        }
        return new MTLBoxStruct(output, bitmap, (System.nanoTime() - startTime) / 1_000_000);
    }

    private OrtSession createNNSession(File file, OrtEnvironment env) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        EnumSet<NNAPIFlags> nnapiFlags = EnumSet.of(
                NNAPIFlags.CPU_DISABLED,
                NNAPIFlags.USE_FP16
        );
        options.addNnapi(nnapiFlags);
        try {
            return env.createSession(file.getPath(), options);
        } catch (OrtException e) {
            return createSession(file, env);
        }
    }

    private OrtSession createSession(File file, OrtEnvironment env) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        return env.createSession(file.getPath(), options);
    }

}
