package com.example.arbenchapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Menu;

import com.example.arbenchapp.datatypes.ImagePage;
import com.example.arbenchapp.datatypes.ImagePageAdapter;
import com.example.arbenchapp.datatypes.MTLBoxStruct;
import com.example.arbenchapp.datatypes.RunType;
import com.example.arbenchapp.datatypes.Settings;
import com.example.arbenchapp.monitor.HardwareMonitor;
import com.example.arbenchapp.ui.settings.SettingsFragment;
import com.example.arbenchapp.util.ConversionUtil;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.arbenchapp.databinding.ActivityMainBinding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    // Registers a photo picker activity launcher in single-select mode.
    ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                // Callback is invoked after the user selects a media item or closes the
                // photo picker
                if (uri != null) {
                    Log.d("PhotoPicker", "Selected URI: " + uri);
                    try {
                        // BUG: switching tabs messes with image positioning
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                        RunType runType = RunType.SWIN_MTL;
                        Settings s = new Settings(
                                runType, ConversionUtil.getConversionMap(runType), 224, 224);
                        MTLBox mtlBox = new MTLBox(s);

                        MTLBoxStruct processed = mtlBox.run(bitmap, this);
                        Map<String, Bitmap> bms = processed.getBitmaps();

                        ViewPager2 viewPager2 = findViewById(R.id.viewPager);
                        ArrayList<ImagePage> pages = new ArrayList<>();
                        ImagePage inputIp = new ImagePage(processed.getInput(), "Input Image");
                        pages.add(inputIp);
                        for (Map.Entry<String, Bitmap> entry : bms.entrySet()) {
                            ImagePage ip = new ImagePage(entry.getValue(), createDisplayString(entry.getKey(), processed));
                            pages.add(ip);
                        }
                        ImagePageAdapter adapter = new ImagePageAdapter(pages);
                        viewPager2.setAdapter(adapter);
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

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (!prefs.getBoolean("use_camera", false)) {
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                } else {
                    // camera is being used!
                }
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
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
        // Inflate the menu; this adds items to the action bar if it is present.
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

    public String createDisplayString(String output, MTLBoxStruct processed) {
        Double tm = processed.getTime();
        HardwareMonitor.HardwareMetrics metrics = processed.getMetrics();
        String[] messages = getMessages(tm, metrics);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String nullMetricsMessage = "ERR: Returned NULL metrics";
        int decimalPoints = 2;
        String timeDisplay = prefs.getBoolean("runtime_model", true) ?
                "Runtime: " + tm + " ms" : "";
        String timeDisplayProcessing = prefs.getBoolean("runtime_total", true) ?
                "Runtime with Postprocessing: " + metrics.executionWithProcessing + "ms" : "";
        String cpuUsagePercentDisplay = prefs.getBoolean("cpu_usage", true) ?
                "CPU Usage Percent: " + round(metrics.cpuUsagePercent, decimalPoints) + "%" : "";
        String cpuUsageDeltaDisplay = prefs.getBoolean("cpu_usage_delta", true) ?
                "CPU Usage Percent Delta: " + round(metrics.cpuUsageDelta, decimalPoints) + "%" : "";
        String threadCpuTimeDisplay = prefs.getBoolean("cpu_thread_time", true) ?
                "Thread CPU Time: " + round(metrics.threadCpuTimeMs, decimalPoints) + "ms" : "";
        String memoryUsedDisplay = prefs.getBoolean("memory_usage", true) ?
                "Memory Used: " + round(metrics.memoryUsedBytes, decimalPoints) + " bytes" : "";
        String batteryUsedDisplay = prefs.getBoolean("battery_usage", true) ?
                "Battery Used: " + round(metrics.batteryPercentageUsed, decimalPoints) + "%" : "";
        String powerConsumedDisplay = prefs.getBoolean("power_consumed", true) ?
                "Power Consumed: " + round(metrics.powerConsumedMicroWattHours, decimalPoints) + "mWh" : "";
        String temperatureChangeDisplay = prefs.getBoolean("temp_change", true) ?
                "Temperature Change: " + round(metrics.temperatureChangeCelsius, decimalPoints) + " degrees Celsius" : "";
        String finalTemperatureDisplay = prefs.getBoolean("temp_final", true) ?
                "Final Temperature: " + round(metrics.finalTemperatureCelsius, decimalPoints) + " degrees Celsius" : "";
        String averageCurrentDisplay = prefs.getBoolean("current_avg", true) ?
                "Average Current: " + round(metrics.averageCurrentDrainMicroAmps, decimalPoints) + " microA" : "";
        return new String[]{
                timeDisplay,
                timeDisplayProcessing,
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

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}