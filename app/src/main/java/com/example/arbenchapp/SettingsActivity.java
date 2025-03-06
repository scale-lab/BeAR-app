package com.example.arbenchapp;

import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.arbenchapp.datatypes.StringMappingPreference;
import com.example.arbenchapp.ui.settings.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class MainSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }

    public static class HardwareSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.hw_preferences, rootKey);
        }
    }

    public static class ModelSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.model_preferences, rootKey);

            ListPreference modelFilePreference = findPreference("model_file_selection");
            StringMappingPreference mappingPreference = findPreference("output_option_mappings");

            if (modelFilePreference != null) {
                modelFilePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    // String selectedModelFile = newValue.toString();
                    return true;
                });
            }

            if (mappingPreference != null) {
                mappingPreference.setOptionsList(
                        new String[]{"DEFAULT", "BLACK AND WHITE", "COLOR", "ARGMAX COLOR"}
                );
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull androidx.preference.Preference preference) {
            if (preference instanceof StringMappingPreference) {
                StringMappingPreference.StringMappingDialogFragment dialogFragment =
                        StringMappingPreference.StringMappingDialogFragment.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getParentFragmentManager(), null);
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }
    }

    public static class CameraSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.cam_preferences, rootKey);

            EditTextPreference framePreference = findPreference("update_freq_frames");
            EditTextPreference timePreference = findPreference("update_freq_time");
            EditTextPreference resolutionPreference = findPreference("resolution");

            if (framePreference != null) {
                framePreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                });

                framePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int value = Integer.parseInt(newValue.toString());
                        int minValue = 1;
                        int maxValue = 9000;

                        if (value < minValue) value = minValue;
                        if (value > maxValue) value = maxValue;

                        ((EditTextPreference) preference).setText(String.valueOf(value));

                        return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (timePreference != null) {
                timePreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                });

                timePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int value = Integer.parseInt(newValue.toString());
                        int minValue = 1;
                        int maxValue = 300;

                        if (value < minValue) value = minValue;
                        if (value > maxValue) value = maxValue;

                        ((EditTextPreference) preference).setText(String.valueOf(value));

                        return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (resolutionPreference != null) {
                resolutionPreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_CLASS_TEXT);
                });

                resolutionPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    String input = newValue.toString().trim();

                    // Validate input format (must be two integers separated by a comma)
                    String[] parts = input.split(",");
                    if (parts.length != 2) return false;

                    try {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());

                        int minValue = 0;
                        int maxValue = 500;

                        x = Math.max(minValue, Math.min(maxValue, x));
                        y = Math.max(minValue, Math.min(maxValue, y));

                        ((EditTextPreference) preference).setText(x + "," + y);

                        return false;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }
        }
    }
}
