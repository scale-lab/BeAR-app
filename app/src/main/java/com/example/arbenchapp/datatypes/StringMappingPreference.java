package com.example.arbenchapp.datatypes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.example.arbenchapp.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StringMappingPreference extends DialogPreference {
    private Map<String, String> mappings;
    private String[] optionsList;

    public StringMappingPreference(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);

        // Set dialog layout
        setDialogLayoutResource(R.layout.preference_string_mapping_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);

        setLayoutResource(R.layout.preference_string_mapping);

        optionsList = new String[]{
                "DEFAULT",
                "BLACK AND WHITE",
                "COLOR",
                "ARGMAX COLOR",
                "B&W GRADIENT",
                "COLOR GRADIENT"
        };

        // Load saved mappings
        loadMappings();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        // Display current mappings in the preference view
        LinearLayout mappingsContainer = (LinearLayout) holder.findViewById(R.id.mappings_container);
        mappingsContainer.removeAllViews();

        if (mappings != null && !mappings.isEmpty()) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                TextView pairView = new TextView(getContext());
                String text = entry.getKey() + " → " + entry.getValue();
                pairView.setText(text);
                pairView.setPadding(0, 8, 0, 8);
                mappingsContainer.addView(pairView);
            }
        } else {
            TextView emptyView = new TextView(getContext());
            String text = "No mappings configured";
            emptyView.setText(text);
            emptyView.setPadding(0, 8, 0, 8);
            mappingsContainer.addView(emptyView);
        }
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> newMappings) {
        mappings = newMappings;

        // Convert to JSON and save
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        Gson gson = new Gson();
        String json = gson.toJson(mappings);

        prefs.edit().putString(getKey(), json).apply();

        // Notify change
        notifyChanged();
    }

    private void loadMappings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String json = prefs.getString(getKey(), "");

        if (json.isEmpty()) {
            mappings = new HashMap<>();
        } else {
            Gson gson = new Gson();
            Type type = new TypeToken<HashMap<String, String>>(){}.getType();
            mappings = gson.fromJson(json, type);
        }
    }

    public String[] getOptionsList() {
        return optionsList;
    }

    public void setOptionsList(String[] options) {
        this.optionsList = options;
    }

    // Create the dialog fragment for this preference
    public static class StringMappingDialogFragment extends PreferenceDialogFragmentCompat {
        private LinearLayout mappingsLayout;
        private final List<MappingRow> mappingRows = new ArrayList<>();

        public static StringMappingDialogFragment newInstance(String key) {
            StringMappingDialogFragment fragment = new StringMappingDialogFragment();
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        // Class to hold references to row views
        private static class MappingRow {
            EditText inputEdit;
            Spinner optionSpinner;

            public MappingRow(EditText input, Spinner spinner) {
                inputEdit = input;
                optionSpinner = spinner;
            }

            public String getInputValue() {
                return inputEdit.getText().toString().trim();
            }

            public String getSelectedOption() {
                return optionSpinner.getSelectedItem().toString();
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Set the dialog to adjust for soft keyboard
            setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_Alert);
        }

        @Override
        protected View onCreateDialogView(@NonNull Context context) {
            return super.onCreateDialogView(context);
        }

        @Override
        protected void onBindDialogView(@NonNull View view) {
            super.onBindDialogView(view);

            mappingsLayout = view.findViewById(R.id.mappings_layout);
            Button addButton = view.findViewById(R.id.add_mapping_button);

            // Get current preference
            StringMappingPreference preference = (StringMappingPreference) getPreference();
            Map<String, String> currentMappings = preference.getMappings();

            // Add existing mappings
            if (currentMappings != null && !currentMappings.isEmpty()) {
                for (Map.Entry<String, String> entry : currentMappings.entrySet()) {
                    addMappingRow(entry.getKey(), entry.getValue(), preference.getOptionsList());
                }
            } else {
                // Add an empty row if there are no mappings
                addMappingRow("", preference.getOptionsList()[0], preference.getOptionsList());
            }

            // Add button listener
            addButton.setOnClickListener(v -> {
                MappingRow row = addMappingRow("", preference.getOptionsList()[0], preference.getOptionsList());
                if (row != null && row.inputEdit != null) {
                    forceShowKeyboard(row.inputEdit);
                }
            });
        }

        private void forceShowKeyboard(final EditText editText) {
            editText.requestFocus();

            // Post with a delay to make sure the view is fully initialized
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isAdded() && getContext() != null) {
                        InputMethodManager imm = (InputMethodManager)
                                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                        }
                    }
                }
            }, 200); // 200ms delay
        }

        private MappingRow addMappingRow(String inputValue, String selectedOption, String[] options) {
            Context context = getContext();
            if (context == null) return null;

            // Create row layout
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            rowLayout.setPadding(0, 8, 0, 8);

            // Create input field with focusable true and focusableInTouchMode true
            final EditText inputField = getEditText(inputValue, context);

            // Create spinner for options
            Spinner optionSpinner = new Spinner(context);
            optionSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context, android.R.layout.simple_spinner_dropdown_item, options);
            optionSpinner.setAdapter(adapter);

            // Select the current option if it exists
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(selectedOption)) {
                    optionSpinner.setSelection(i);
                    break;
                }
            }

            // Create delete button
            Button deleteButton = getDeleteButton(context, rowLayout, inputField);

            // Add views to row
            rowLayout.addView(inputField);
            rowLayout.addView(optionSpinner);
            rowLayout.addView(deleteButton);

            // Add row to layout
            mappingsLayout.addView(rowLayout);

            // Store references
            MappingRow newRow = new MappingRow(inputField, optionSpinner);
            mappingRows.add(newRow);

            return newRow;
        }

        private @NonNull EditText getEditText(String inputValue, Context context) {
            final EditText inputField = new EditText(context);
            inputField.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            inputField.setInputType(InputType.TYPE_CLASS_TEXT);
            inputField.setText(inputValue);
            inputField.setFocusable(true);
            inputField.setFocusableInTouchMode(true);

            // Custom click behavior to force keyboard
            inputField.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    forceShowKeyboard(inputField);
                }
            });
            return inputField;
        }

        private @NonNull Button getDeleteButton(Context context, LinearLayout rowLayout, EditText inputField) {
            Button deleteButton = new Button(context);
            deleteButton.setText("×");
            deleteButton.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            deleteButton.setOnClickListener(v -> {
                mappingsLayout.removeView(rowLayout);
                mappingRows.removeIf(row -> row.inputEdit == inputField);
            });
            return deleteButton;
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                StringMappingPreference preference = (StringMappingPreference) getPreference();
                Map<String, String> newMappings = new HashMap<>();

                // Collect values from rows
                for (MappingRow row : mappingRows) {
                    String input = row.getInputValue();
                    if (!input.isEmpty()) {
                        newMappings.put(input, row.getSelectedOption());
                    }
                }

                // Save the new mappings
                preference.setMappings(newMappings);
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Focus the first input field if there are any
            if (!mappingRows.isEmpty()) {
                MappingRow firstRow = mappingRows.get(0);
                if (firstRow != null && firstRow.inputEdit != null) {
                    // Use a delayed post to ensure view is ready
                    new Handler().postDelayed(() -> {
                        if (isAdded()) {
                            forceShowKeyboard(firstRow.inputEdit);
                        }
                    }, 300);
                }
            }
        }
    }
}
