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

import static android.graphics.Color.TRANSPARENT;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static com.krypton.updater.util.Constants.REFRESH_INTERVAL_KEY;
import static com.krypton.updater.util.Constants.THEME_KEY;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.krypton.updater.R;
import com.krypton.updater.util.Utils;
import com.krypton.updater.UpdaterApplication;

import javax.inject.Inject;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final VibrationEffect click = VibrationEffect.createPredefined(EFFECT_CLICK);
    private SharedPreferences sharedPrefs;
    private Editor editor;
    private SeekBarPreference seekBar;
    private Vibrator vibrator;
    private int currThemeMode;

    @Inject
    public void setDependencies(SharedPreferences prefs) {
        sharedPrefs = prefs;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        setPreferencesFromResource(R.xml.settings_fragment, key);
        ((UpdaterApplication) getActivity().getApplication())
            .getComponent().inject(this);
        vibrator = getContext().getSystemService(Vibrator.class);
        editor = sharedPrefs.edit();
        currThemeMode = sharedPrefs.getInt(THEME_KEY, 2);
        seekBar = getPreferenceScreen().findPreference(REFRESH_INTERVAL_KEY);
        seekBar.setValue(sharedPrefs.getInt(REFRESH_INTERVAL_KEY, 7));
        seekBar.setUpdatesContinuously(true);
        seekBar.setOnPreferenceChangeListener((preference, newValue) -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(click);
            }
            updateSharedPrefs(REFRESH_INTERVAL_KEY, (Integer) newValue);
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(THEME_KEY)) {
            showPickerDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void showPickerDialog() {
        AlertDialog themePickerDialog = new Builder(getActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.theme_chooser_dialog_title)
            .setSingleChoiceItems(R.array.theme_modes,
                    currThemeMode, (dialog, which) -> {
                        dialog.dismiss();
                        currThemeMode = which;
                        updateSharedPrefs(THEME_KEY, currThemeMode);
                        Utils.setTheme(currThemeMode);
                    })
            .create();
        themePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(TRANSPARENT));
        themePickerDialog.show();
    }

    private void updateSharedPrefs(String key, int value) {
        editor.putInt(key, value).apply();
    }
}
