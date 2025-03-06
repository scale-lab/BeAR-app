package com.example.arbenchapp.datatypes;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AssetFilePreference extends ListPreference {
    public AssetFilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        List<String> fileNames = getAssetsFileList(context);

        String[] fileArray = fileNames.toArray(new String[0]);

        setEntries(fileArray);
        setEntryValues(fileArray);
    }

    // Method to get list of files from assets folder
    private List<String> getAssetsFileList(Context context) {
        List<String> files = new ArrayList<>();
        try {
            String[] assetFiles = context.getAssets().list("");

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
