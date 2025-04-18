package com.example.arbenchapp;

import static java.lang.Math.abs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements CameraUtil.CameraCallback {
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private CameraUtil cameraUtil;
    private ImagePageAdapter adapter;
    private List<ImagePage> imagePageList;
    private MTLBox mtlBox;
    private Resolution res;
    private final ExecutorService inferenceExecutor = Executors.newFixedThreadPool(1);
    private final Object processingLock = new Object();
    private volatile boolean isProcessingInference = false;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

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

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraUtil = new CameraUtil(context, context);

        ViewPager2 viewPager2 = findViewById(R.id.viewPager);
        imagePageList = new ArrayList<>();
        adapter = new ImagePageAdapter(context, imagePageList);
        viewPager2.setAdapter(adapter);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        res = new Resolution(prefs.getString("resolution", "224,224"));
        Settings s = new Settings(res.getHeight(), res.getWidth());
        mtlBox = new MTLBox(s, this, this);

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String s) {
                System.out.println("ONNX preferences changed");
                res = new Resolution(sharedPreferences.getString("resolution", "224,224"));
                Settings new_s = new Settings(res.getHeight(), res.getWidth());
                mtlBox = new MTLBox(new_s, context, context);
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
        runOnUiThread(() -> {
            ImagePage ip = new ImagePage(bitmap, "Input Image");
            if (imagePageList.isEmpty()) {
                imagePageList.add(ip);
                adapter.notifyItemChanged(0);
            } else {
                imagePageList.set(0, ip);
                adapter.notifyItemChanged(0);
            }
        });

        if (prefs.getBoolean("run_inference", true)) {
            synchronized (processingLock) {
                if (isProcessingInference) {
                    return;
                }
                isProcessingInference = true;
            }
            inferenceExecutor.execute(() -> {
                if (!prefs.getBoolean("split_inference", false)) {
                    updateDisplay(bitmap);
                    synchronized (processingLock) {
                        isProcessingInference = false;
                    }
                } else if (!prefs.getBoolean("split_pipeline", false)) {
                    // run sequentially
                    MTLBoxStruct processed = mtlBox.run(bitmap); // will run split inference
                    updateDisplay(processed);
                    synchronized (processingLock) {
                        isProcessingInference = false;
                    }
                } else {
                    mtlBox.run(bitmap);
                }
            });
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraUtil != null) {
            cameraUtil.shutdown();
        }
        if (inferenceExecutor != null && !inferenceExecutor.isShutdown()) {
            inferenceExecutor.shutdown();
        }
        if (prefs != null && listener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    public void unlock() {
        synchronized (processingLock) {
            isProcessingInference = false;
        }
    }

    public void updateDisplay(MTLBoxStruct processed) {
        try {
            Map<String, Bitmap> bms = processed.getBitmaps();
            final List<ImagePage> newPages = new ArrayList<>();
            boolean write = true;
            for (Map.Entry<String, Bitmap> entry : bms.entrySet()) {
                newPages.add(new ImagePage(entry.getValue(), createDisplayString(entry.getKey(), processed, write)));
                write = false;
            }
            runOnUiThread(() -> {
                System.out.println("ONNX adding pages");
                for (int i = 0; i < newPages.size(); i++) {
                    if (imagePageList.size() < i + 2) {
                        imagePageList.add(newPages.get(i));
                    } else {
                        if (processed.hasOldMetrics()) {
                            System.out.println("ONNX no new metrics");
                            ImagePage hasPrevMetrics = new ImagePage(newPages.get(i).getImage(), imagePageList.get(i + 1).getCaption());
                            imagePageList.set(i + 1, hasPrevMetrics);
                        } else {
                            imagePageList.set(i + 1, newPages.get(i));
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            Log.e("MainActivity", "ONNX Error processing frame", e);
            runOnUiThread(() -> {
                Bitmap disp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                if (processed != null) {
                    disp = processed.getInput();
                }
                ImagePage ip = new ImagePage(disp, "Error processing image: " + e.getMessage());
                if (imagePageList.size() < 2) {
                    imagePageList.add(ip);
                } else {
                    imagePageList.set(1, ip);
                }
                adapter.notifyDataSetChanged();
            });
        }
    }

    public void updateDisplay(Bitmap bitmap) {
        try {
            MTLBoxStruct processed = mtlBox.run(bitmap);
            Map<String, Bitmap> bms = processed.getBitmaps();
            final List<ImagePage> newPages = new ArrayList<>();
            boolean write = true;
            for (Map.Entry<String, Bitmap> entry : bms.entrySet()) {
                newPages.add(new ImagePage(entry.getValue(), createDisplayString(entry.getKey(), processed, write)));
                write = false;
            }
            runOnUiThread(() -> {
                System.out.println("ONNX adding pages");
                for (int i = 0; i < newPages.size(); i++) {
                    if (imagePageList.size() < i + 2) {
                        imagePageList.add(newPages.get(i));
                    } else {
                        if (processed.hasOldMetrics()) {
                            System.out.println("ONNX no new metrics");
                            ImagePage hasPrevMetrics = new ImagePage(newPages.get(i).getImage(), imagePageList.get(i + 1).getCaption());
                            imagePageList.set(i + 1, hasPrevMetrics);
                        } else {
                            imagePageList.set(i + 1, newPages.get(i));
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            Log.e("MainActivity", "ONNX Error processing frame", e);
            runOnUiThread(() -> {
                ImagePage ip = new ImagePage(bitmap, "Error processing image: " + e.getMessage());
                if (imagePageList.size() < 2) {
                    imagePageList.add(ip);
                } else {
                    imagePageList.set(1, ip);
                }
                adapter.notifyDataSetChanged();
            });
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