/*
 * Copyright (C) 2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krypton.updater.ui.fragment;

import static com.krypton.updater.util.Constants.REFRESH_INTERVAL_KEY;
import static com.krypton.updater.util.Constants.THEME_KEY;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.krypton.updater.R;
import com.krypton.updater.viewmodel.AppViewModel;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final VibrationEffect click = VibrationEffect.createPredefined(
        VibrationEffect.EFFECT_CLICK);
    private AppViewModel viewModel;
    private Vibrator vibrator;
    private AlertDialog themePickerDialog;

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        setPreferencesFromResource(R.xml.settings_fragment, key);
        final FragmentActivity activity = requireActivity();
        viewModel = new ViewModelProvider(activity).get(AppViewModel.class);
        vibrator = activity.getSystemService(Vibrator.class);
        SeekBarPreference seekBar = findPreference(REFRESH_INTERVAL_KEY);
        seekBar.setValue(viewModel.getRefreshInterval());
        seekBar.setUpdatesContinuously(true);
        seekBar.setOnPreferenceChangeListener((preference, newValue) -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(click);
            }
            viewModel.updateRefreshInterval((Integer) newValue);
            return true;
        });
        findPreference(THEME_KEY).setOnPreferenceClickListener(pref -> {
            showPickerDialog();
            return true;
        });
    }

    private void showPickerDialog() {
        if (themePickerDialog == null) {
            themePickerDialog = new Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.theme_chooser_dialog_title)
                .setSingleChoiceItems(R.array.theme_modes, viewModel.getAppThemeMode(),
                        (dialog, which) -> {
                            dialog.dismiss();
                            viewModel.updateThemeMode(which);
                        })
                .create();
        }
        themePickerDialog.show();
    }
}
