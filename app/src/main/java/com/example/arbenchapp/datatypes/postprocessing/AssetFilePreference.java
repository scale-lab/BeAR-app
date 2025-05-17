package com.example.arbenchapp.datatypes.postprocessing;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import com.example.arbenchapp.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AssetFilePreference extends ListPreference {
    private static final String DEFAULT_DIRECTORY = "full";

    public AssetFilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AssetFilePreference);
        String directory = ta.getString(R.styleable.AssetFilePreference_assetsDirectory);
        if (directory == null || directory.isEmpty()) {
            directory = DEFAULT_DIRECTORY;
        }
        ta.recycle();

        List<String> fileNames = getAssetsFileList(context, directory);

        String[] fileArray = fileNames.toArray(new String[0]);

        setEntries(fileArray);
        setEntryValues(fileArray);
    }

    // Method to get list of files from assets folder
    private List<String> getAssetsFileList(Context context, String directory) {
        List<String> files = new ArrayList<>();
        try {
            String[] assetFiles = context.getAssets().list(directory);

            if (assetFiles != null) {
                for (String file : assetFiles) {
                    if (file.endsWith(".onnx") || file.endsWith(".pt")) {
                        files.add(file);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }
}
