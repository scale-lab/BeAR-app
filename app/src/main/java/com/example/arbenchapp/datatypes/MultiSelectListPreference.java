package com.example.arbenchapp.datatypes;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import com.example.arbenchapp.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {
    private static final String DEFAULT_DIRECTORY = "decoders";
    private CharSequence[] values = new CharSequence[0];
    private CharSequence[] entryValues = new CharSequence[0];
    private Set<String> selectedValues = new HashSet<>();
    private String assetsDirectory;

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // Load persisted values or use default
        selectedValues = getPersistedStringSet(new HashSet<>());
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // Handle default value from XML
        final CharSequence[] defaultEntries = a.getTextArray(index);
        Set<String> result = new HashSet<>();
        if (defaultEntries != null) {
            for (CharSequence entry : defaultEntries) {
                result.add(entry.toString());
            }
        }
        return result;
    }

    public MultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AssetFilePreference);
        assetsDirectory = a.getString(R.styleable.AssetFilePreference_assetsDirectory);
        a.recycle();

        if (assetsDirectory == null) assetsDirectory = "decoders";
        loadAssetsFiles(context);
    }

    private void loadAssetsFiles(Context context) {
        try {
            String[] files = context.getAssets().list(assetsDirectory);
            if (files == null || files.length == 0) {
                Log.e("AssetPref", "No files in directory: " + assetsDirectory);
                return;
            }

            List<CharSequence> entriesList = new ArrayList<>();
            List<CharSequence> valuesList = new ArrayList<>();

            for (String file : files) {
                entriesList.add(file.replaceFirst("[.][^.]+$", "")); // Remove extension
                valuesList.add(assetsDirectory + "/" + file);
            }

            values = entriesList.toArray(new CharSequence[0]);
            entryValues = valuesList.toArray(new CharSequence[0]);

        } catch (IOException e) {
            Log.e("AssetPref", "Error loading assets: " + assetsDirectory, e);
        }
    }

    public void persistValues(Set<String> values) {
        this.selectedValues = values;
        persistStringSet(values);
        notifyChanged();
    }

    // Implement summary updating
    @Override
    public CharSequence getSummary() {
        return TextUtils.join(", ", values);
    }

    public CharSequence[] getEntries() {
        return this.values;
    }

    public CharSequence[] getEntryValues() {
        return this.entryValues;
    }

    public Set<String> getValues() {
        return this.selectedValues;
    }

    public void setValues(Set<String> selectedValues) {
        this.selectedValues.clear();
        this.selectedValues.addAll(selectedValues);
        persistStringSet(this.selectedValues);
        notifyChanged();
    }
}