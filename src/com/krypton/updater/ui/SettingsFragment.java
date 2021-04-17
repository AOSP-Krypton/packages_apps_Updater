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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.krypton.updater.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String THEME_KEY = "theme_settings_preference";
    private static SharedPreferences mPrefs;
    private static SharedPreferences.Editor mEditor;
    private static int mCurThemeMode;
    private static SelectThemeFragment mFragment;

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        setPreferencesFromResource(R.xml.settings_fragment, key);
        mPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        mCurThemeMode = mPrefs.getInt(THEME_KEY, 2);
        mEditor = mPrefs.edit();
        mFragment = new SelectThemeFragment();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null) {
            if (key.equals(THEME_KEY)) {
                mFragment.show(getParentFragmentManager(), null);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    public static class SelectThemeFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
            builder.setTitle(R.string.theme_chooser_dialog_title)
                   .setSingleChoiceItems(R.array.theme_modes,
                        mCurThemeMode, (dialog, which) -> updateTheme(which));
            return builder.create();
        }
    }

    private static void updateTheme(int mode) {
        if (mode != mCurThemeMode) {
            switch (mode) {
                case 0:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case 1:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case 2:
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            mCurThemeMode = mode;
            mEditor.putInt(THEME_KEY, mode);
            mEditor.apply();
        }
    }

}
