package com.example.arbenchapp;

import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.arbenchapp.datatypes.MultiSelectListPreference;
import com.example.arbenchapp.datatypes.StringMappingPreference;
import com.example.arbenchapp.ui.settings.MultiSelectDialogFragment;
import com.example.arbenchapp.ui.settings.SettingsFragment;

import java.util.Set;

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

            Preference modelSettingsButton = findPreference("model_settings");
            if (modelSettingsButton != null) {
                modelSettingsButton.setOnPreferenceClickListener(preference -> {
                    // Launch ModelSettingsFragment when clicked
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.settings_container, new ModelSettingsFragment())
                            .addToBackStack(null)
                            .commit();
                    return true;
                });
            }
        }
    }

    public static class HardwareSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.hw_preferences, rootKey);
        }
    }

    public static class ModelSettingsFragment extends PreferenceFragmentCompat
            implements MultiSelectDialogFragment.MultiSelectListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.model_preferences, rootKey);
        }

        // Implement interface method
        @Override
        public void onMultiSelectResult(String preferenceKey, Set<String> selectedValues) {
            if (preferenceKey == null) return;

            MultiSelectListPreference pref = findPreference(preferenceKey);
            if (pref != null) {
                pref.setValues(selectedValues);
                pref.persistStringSet(selectedValues); // Force immediate save
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof MultiSelectListPreference) {
                MultiSelectListPreference multiPref = (MultiSelectListPreference) preference;

                // Validate key exists
                String key = multiPref.getKey();
                if (key == null) {
                    throw new IllegalStateException("Preference must have a key");
                }

                // Validate entries/values
                CharSequence[] entries = multiPref.getEntries() != null ? multiPref.getEntries() : new CharSequence[0];
                CharSequence[] values = multiPref.getEntryValues() != null ? multiPref.getEntryValues() : new CharSequence[0];

                MultiSelectDialogFragment dialog = MultiSelectDialogFragment.newInstance(
                        entries,
                        values,
                        multiPref.getValues(),
                        key
                );
                dialog.setTargetFragment(this, 0);
                dialog.show(getParentFragmentManager(), "multi_select");
            } else if (preference instanceof StringMappingPreference) {
                // Create an instance of the dialog fragment
                DialogFragment dialogFragment = StringMappingPreference.StringMappingDialogFragment.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                // Show the dialog
                dialogFragment.show(getParentFragmentManager(), "StringMappingPreference");
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            // Force persist values when leaving fragment
            MultiSelectListPreference pref = findPreference("decoder_selection");
            if (pref != null) {
                pref.persistStringSet(pref.getValues());
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
