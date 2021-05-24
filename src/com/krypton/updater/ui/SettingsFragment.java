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

package com.krypton.updater.ui;

import static android.graphics.Color.TRANSPARENT;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.krypton.updater.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    protected static final String THEME_KEY = "theme_settings_preference";
    private SharedPreferences sharedPrefs;
    private int currThemeMode;

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        setPreferencesFromResource(R.xml.settings_fragment, key);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        currThemeMode = sharedPrefs.getInt(THEME_KEY, 2);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key.equals(THEME_KEY)) {
            AlertDialog themePickerDialog = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                .setTitle(R.string.theme_chooser_dialog_title)
                .setSingleChoiceItems(R.array.theme_modes,
                        currThemeMode, (dialog, which) -> {
                            dialog.dismiss();
                            currThemeMode = which;
                            sharedPrefs.edit()
                                .putInt(THEME_KEY, currThemeMode)
                                .apply();
                            UpdaterActivity.setAppTheme(currThemeMode);
                        })
                .create();
            themePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(TRANSPARENT));
            themePickerDialog.show();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
