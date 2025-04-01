package com.example.arbenchapp;

import static java.lang.Math.abs;

import android.app.ActivityManager;
import android.app.GameManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Menu;

import com.example.arbenchapp.datatypes.ImagePage;
import com.example.arbenchapp.datatypes.ImagePageAdapter;
import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.Resolution;
import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.improvemodels.BitmapPool;
import com.example.arbenchapp.monitor.HardwareMonitor;
import com.example.arbenchapp.util.CameraUtil;
import com.example.arbenchapp.util.ConversionUtil;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.example.arbenchapp.databinding.ActivityMainBinding;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ai.onnxruntime.OrtException;

public class MainActivity extends AppCompatActivity implements CameraUtil.CameraCallback {
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private CameraUtil cameraUtil;
    private ImagePageAdapter adapter;
    private List<ImagePage> imagePageList;
    private MTLBox mtlBox;
    private Resolution res;
    private BitmapPool bitmapPool;
    private Bitmap currentDisplayBitmap;
    private Bitmap latestCameraFrame;
    private Matrix rotationMatrix;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "InferenceThread");
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            }
        }
    );
    private final Object processingLock = new Object();
    private final Object displayLock = new Object();
    private boolean isProcessingInference = false;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private long totalNative = 0;
    private long gap;

    // Registers a photo picker activity launcher in single-select mode.
    ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                // Callback is invoked after the user selects a media item or closes the
                // photo picker
                if (uri != null) {
                    Log.d("PhotoPicker", "Selected URI: " + uri);
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);

                        MTLBoxStruct processed = mtlBox.run(bitmap);
                        Map<String, Bitmap> bms = processed.getBitmaps();

                        ImagePage inputIp = new ImagePage(processed.getInput(), "Input Image");
                        imagePageList.clear();
                        adapter.notifyDataSetChanged();
                        imagePageList.add(inputIp);
                        adapter.notifyItemChanged(0);
                        boolean write = true;
                        for (Map.Entry<String, Bitmap> entry : bms.entrySet()) {
                            imagePageList.add(new ImagePage(entry.getValue(), createDisplayString(entry.getKey(), processed, write)));
                            adapter.notifyItemChanged(imagePageList.size() - 1);
                            write = false;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity context = this;

        // Only call this for Android 12 and higher devices
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
            // Get GameManager from SystemService
            GameManager gameManager = this.getSystemService(GameManager.class);

            // Returns the selected GameMode
            int gameMode = gameManager.getGameMode();
            System.out.println("GAMEMODE_9832: " + gameMode);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        gap = 20; // 20 ms
        cameraUtil = new CameraUtil(context, context, gap);

        ViewPager2 viewPager2 = findViewById(R.id.viewPager);
        imagePageList = new ArrayList<>();
        adapter = new ImagePageAdapter(context, imagePageList);
        viewPager2.setAdapter(adapter);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        res = new Resolution(prefs.getString("resolution", "224,224"));
        Settings s = new Settings(res.getHeight(), res.getWidth());
        mtlBox = new MTLBox(s, this);

        // TODO: Should be related to number of inputs and outputs
        bitmapPool = new BitmapPool(2, res.getWidth(), res.getHeight(), Bitmap.Config.ARGB_8888);

        rotationMatrix = new Matrix();
        rotationMatrix.postRotate(90); // TODO: dawg

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
                System.out.println("ONNX preferences changed");
                res = new Resolution(sharedPreferences.getString("resolution", "224,224"));
                Settings new_s = new Settings(res.getHeight(), res.getWidth());
                try {
                    mtlBox.shutdown();
                } catch (OrtException e) {
                    throw new RuntimeException(e);
                }
                mtlBox = new MTLBox(new_s, context);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!prefs.getBoolean("use_camera", false)) {
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                } else {
                    // camera is being used!
                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(context, new String[]{android.Manifest.permission.CAMERA}, 101);
                        return;
                    }
                    cameraUtil.startCamera();
                }
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onFrameCaptured(Bitmap bitmap) {
        synchronized (displayLock) {
            if (latestCameraFrame != null && !latestCameraFrame.isRecycled()) {
                latestCameraFrame.recycle();
            }
            latestCameraFrame = bitmap.copy(bitmap.getConfig(), true);
        }

        updateDisplayBitmap();
        try {
            if (prefs.getBoolean("run_inference", true)) {
                processFrameForInference();
            } else {
                runOnUiThread(() -> {
                    ImagePage ip = new ImagePage(bitmap, "Test Second Image");
                    if (imagePageList.size() < 2) {
                        imagePageList.add(ip);
                        adapter.notifyItemChanged(imagePageList.size() - 1);
                    } else {
                        imagePageList.set(1, ip);
                        adapter.notifyItemChanged(1);
                    }
                });
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error processing frame", e);
        }

    }

    private void updateDisplayBitmap() {
        synchronized (displayLock) {
            if (latestCameraFrame == null || latestCameraFrame.isRecycled()) return;

            Bitmap displayBitmap = latestCameraFrame.copy(latestCameraFrame.getConfig(), true);
            runOnUiThread(() -> {
                currentDisplayBitmap = displayBitmap;
                if (imagePageList.isEmpty()) {
                    ImagePage ip = new ImagePage(currentDisplayBitmap, "Input Image");
                    imagePageList.add(ip);
                    adapter.notifyItemInserted(0);
                } else {
                    imagePageList.get(0).setImage(latestCameraFrame);
                    // imagePageList.set(0, ip);
                    // adapter.notifyItemChanged(0);
                }
            });
        }
    }

    private void processFrameForInference() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memInfo);
        Log.d("MEMORY", "Available: " + memInfo.availMem + " Threshold: " + memInfo.threshold);
        Debug.MemoryInfo memInfoD = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfoD);

        synchronized (processingLock) {
            if (isProcessingInference) {
                return;
            }
            isProcessingInference = true;
        }

        Bitmap inferenceBitmap;
        synchronized (displayLock) {
            if (latestCameraFrame == null || latestCameraFrame.isRecycled()) {
                synchronized (processingLock) {
                    isProcessingInference = false;
                }
                return;
            }
            inferenceBitmap = latestCameraFrame.copy(latestCameraFrame.getConfig(), true);
        }

        inferenceExecutor.execute(() -> {
            try {
                long nativeMemBefore = Debug.getNativeHeapAllocatedSize();
                MTLBoxStruct processed = mtlBox.run(inferenceBitmap);
                long nativeMemAfter = Debug.getNativeHeapAllocatedSize();
                long allocated = nativeMemAfter - nativeMemBefore;
                totalNative += allocated;
                Log.d("NATIVE_MEM", "Allocated: " + allocated + " bytes");
                Log.d("NATIVE_MEM", "Total Allocated: " + totalNative + " bytes");
                handleInferenceResults(processed);
            } catch (Exception e) {
                Log.e("MainActivity", "ONNX Error processing frame", e);
                runOnUiThread(() -> {
                    ImagePage ip = new ImagePage(inferenceBitmap, "Error processing image: " + e.getMessage());
                    if (imagePageList.size() < 2) {
                        imagePageList.add(ip);
                    } else {
                        imagePageList.set(1, ip);
                    }
                    adapter.notifyDataSetChanged();
                    bitmapPool.release(inferenceBitmap);
                });
            } finally {
                inferenceBitmap.recycle();
                synchronized (processingLock) {
                    isProcessingInference = false;
                }
                processFrameForInference();
            }
        });
    }

    private void handleInferenceResults(MTLBoxStruct processed) {
        Map<String, Bitmap> bms = processed.getBitmaps();
        final List<ImagePage> newPages = new ArrayList<>();
        cameraUtil.updateFramerate((long) Math.ceil(processed.getMetrics().fps));

        boolean write = true;
        for (Map.Entry<String, Bitmap> entry : bms.entrySet()) {
            // TODO: Better lifecycle management for output bitmaps
            newPages.add(new ImagePage(entry.getValue(), createDisplayString(entry.getKey(), processed, write)));
            write = false;
        }

        runOnUiThread(() -> {
            System.out.println("ONNX adding pages");
            for (int i = 0; i < newPages.size(); i++) {
                int position = i + 1;
                if (position < imagePageList.size()) {
                    if (processed.hasOldMetrics()) {
                        System.out.println("ONNX no new metrics");
                        ImagePage hasPrevMetrics =
                                new ImagePage(newPages.get(i).getImage(), imagePageList.get(position).getCaption());
                        imagePageList.set(position, hasPrevMetrics);
                    } else {
                        imagePageList.set(position, newPages.get(i));
                    }
                } else {
                    imagePageList.add(newPages.get(i));
                }
            }
            adapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraUtil != null) {
            cameraUtil.shutdown();
        }
        if (bitmapPool != null) {
            bitmapPool.clear();
        }
        if (inferenceExecutor != null && !inferenceExecutor.isShutdown()) {
            inferenceExecutor.shutdownNow();
        }
        if (prefs != null && listener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    public String createDisplayString(String output, MTLBoxStruct processed, boolean write) {
        if (processed.hasOldMetrics() && prefs.getBoolean("use_camera", false)) {
            return "Processing...";
        }
        Double tm = processed.getTime();
        HardwareMonitor.HardwareMetrics metrics = processed.getMetrics();
        String[] messages = getMessages(tm, metrics);
        if (write) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH-mm-ss");
            String dtfTime = now.format(dtf);
            String filename = "log_" + LocalDate.now() + "_" + dtfTime + "_" + prefs.getString("model_file_selection", "UNKNOWN-FILE").split("\\.")[0] + ".txt";
            ConversionUtil.logArray(this, messages, filename);
        }
        StringBuilder displayStrBuilt = new StringBuilder();
        displayStrBuilt.append(output).append("\n");
        for (String m : messages) {
            if (!m.isEmpty()) {
                displayStrBuilt.append(m).append("\n");
            }
        }
        String displayStr = displayStrBuilt.toString();
        if (displayStr.endsWith("\n")) {
            displayStr = displayStr.substring(0, displayStr.length() - 1);
        }
        return displayStr;
    }

    private String [] getMessages(Double tm, HardwareMonitor.HardwareMetrics metrics) {
        String nullMetricsMessage = "ERR: Returned NULL metrics";
        int decimalPoints = 2;
        String timeDisplay = prefs.getBoolean("runtime_model", true) ?
                "Inference Time: " + ConversionUtil.round(tm, decimalPoints) + " ms" : "";
        String timeDisplayProcessing = prefs.getBoolean("runtime_total", true) ?
                "Per Frame Runtime: " + ConversionUtil.round(metrics.executionWithProcessing, decimalPoints) + " ms" : "";
        String fps = prefs.getBoolean("fps", true) ?
                "FPS: " + ConversionUtil.round(metrics.fps, decimalPoints) : "";

        String cpuUsagePercentDisplay = prefs.getBoolean("cpu_usage", true) ?
                "CPU Usage Percent: " + ConversionUtil.round(metrics.cpuUsagePercent, decimalPoints) + "%" : "";
        String cpuUsageDeltaDisplay = prefs.getBoolean("cpu_usage_delta", true) ?
                "CPU Usage Percent Delta: " + ConversionUtil.round(metrics.cpuUsageDelta, decimalPoints) + "%" : "";
        String threadCpuTimeDisplay = prefs.getBoolean("cpu_thread_time", true) ?
                "Thread CPU Time: " + ConversionUtil.round(metrics.threadCpuTimeMs, decimalPoints) + " ms" : "";
        String memoryUsedDisplay = prefs.getBoolean("memory_usage", true) ?
                "Memory Difference: " + ConversionUtil.byteString(abs(metrics.memoryUsedBytes), decimalPoints) : "";
        String batteryUsedDisplay = prefs.getBoolean("battery_usage", true) ?
                "Battery Used: " + ConversionUtil.round(metrics.batteryPercentageUsed, decimalPoints) + "%" : "";
        String powerConsumedDisplay = prefs.getBoolean("power_consumed", true) ?
                "Power Consumed: " + ConversionUtil.round(metrics.powerConsumedMicroWattHours, decimalPoints) + " mWh" : "";
        String temperatureChangeDisplay = prefs.getBoolean("temp_change", true) ?
                "Temperature Change: " + ConversionUtil.round(metrics.temperatureChangeCelsius, decimalPoints) + " degrees Celsius" : "";
        String finalTemperatureDisplay = prefs.getBoolean("temp_final", true) ?
                "Final Temperature: " + ConversionUtil.round(metrics.finalTemperatureCelsius, decimalPoints) + " degrees Celsius" : "";
        String averageCurrentDisplay = prefs.getBoolean("current_avg", true) ?
                "Average Current: " + ConversionUtil.round(metrics.averageCurrentDrainMicroAmps, decimalPoints) + " microA" : "";
        return new String[]{
                timeDisplay,
                timeDisplayProcessing,
                fps,
                cpuUsagePercentDisplay,
                cpuUsageDeltaDisplay,
                threadCpuTimeDisplay,
                memoryUsedDisplay,
                batteryUsedDisplay,
                powerConsumedDisplay,
                temperatureChangeDisplay,
                finalTemperatureDisplay,
                averageCurrentDisplay
        };
    }
}