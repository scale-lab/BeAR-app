package com.example.arbenchapp.ui.settings;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

import com.example.arbenchapp.datatypes.postprocessing.MultiSelectListPreference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MultiSelectDialogFragment extends DialogFragment {
    public interface MultiSelectListener {
        void onMultiSelectResult(String preferenceKey, Set<String> selectedValues);
    }

    private static final String ARG_KEY = "preference_key";
    private static final String ARG_ENTRIES = "entries";
    private static final String ARG_VALUES = "values";
    private static final String ARG_SELECTED = "selected";
    private String preferenceKey;

    private MultiSelectListener listener;
    private boolean[] checkedItems;

    public static MultiSelectDialogFragment newInstance(
            CharSequence[] entries,
            CharSequence[] values,
            Set<String> selected,
            String preferenceKey
    ) {
        MultiSelectDialogFragment fragment = new MultiSelectDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequenceArray(ARG_ENTRIES, entries);
        args.putCharSequenceArray(ARG_VALUES, values);
        args.putStringArrayList(ARG_SELECTED, new ArrayList<>(selected));
        args.putString(ARG_KEY, preferenceKey);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure arguments exist
        if (getArguments() == null) {
            dismiss();
            return;
        }
        preferenceKey = getArguments().getString(ARG_KEY);
        if (preferenceKey == null) {
            Log.e("Dialog", "Preference key is null!");
            dismiss();
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (MultiSelectListener) getTargetFragment();
        } catch (ClassCastException e) {
            throw new ClassCastException("Calling fragment must implement MultiSelectListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        // Get arguments with safe defaults
        Bundle args = getArguments();
        preferenceKey = args.getString(ARG_KEY);
        CharSequence[] entries = args != null ? args.getCharSequenceArray(ARG_ENTRIES) : new CharSequence[0];
        CharSequence[] values = args != null ? args.getCharSequenceArray(ARG_VALUES) : new CharSequence[0];
        ArrayList<String> selected = args != null ? args.getStringArrayList(ARG_SELECTED) : new ArrayList<>();

        // Initialize checked state
        checkedItems = new boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            checkedItems[i] = selected.contains(values[i].toString());
        }

        builder.setMultiChoiceItems(entries, checkedItems,
                (dialog, which, isChecked) -> checkedItems[which] = isChecked
        );

        builder.setPositiveButton("OK", (dialog, which) -> {
            Set<String> result = new HashSet<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    result.add(values[i].toString());
                }
            }
            if (listener != null) {
                listener.onMultiSelectResult(preferenceKey, result);
            }
        });

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        MultiSelectListPreference pref = getPreference();
        if (pref == null) {
            Log.e("Dialog", "Preference not found for key: " + preferenceKey);
            dismiss();
            return;
        }

        // Update checkboxes using the actual preference state
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            ListView listView = dialog.getListView();
            CharSequence[] values = pref.getEntryValues();
            if (values != null) {
                Set<String> selected = pref.getValues();
                for (int i = 0; i < values.length; i++) {
                    listView.setItemChecked(i, selected.contains(values[i].toString()));
                }
            }
        }
    }

    private MultiSelectListPreference getPreference() {
        if (preferenceKey == null) return null;

        Fragment target = getTargetFragment();
        if (target instanceof PreferenceFragmentCompat) {
            return ((PreferenceFragmentCompat) target).findPreference(preferenceKey);
        }
        return null;
    }
}
