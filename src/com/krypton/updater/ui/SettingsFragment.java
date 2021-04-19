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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.krypton.updater.R;
import com.krypton.updater.Utils;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static SharedPreferences mPrefs;
    private static SharedPreferences.Editor mEditor;
    private static int mCurThemeMode;
    private SelectThemeFragment mFragment;

    @Override
    public void onCreatePreferences(Bundle bundle, String key) {
        setPreferencesFromResource(R.xml.settings_fragment, key);
        mPrefs = getContext().getSharedPreferences(Utils.SHARED_PREFS, Context.MODE_PRIVATE);
        mCurThemeMode = mPrefs.getInt(Utils.THEME_KEY, 2);
        mEditor = mPrefs.edit();
        mFragment = new SelectThemeFragment();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null) {
            if (key.equals(Utils.THEME_KEY)) {
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
            AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            return dialog;
        }
    }

    private static void updateTheme(int mode) {
        Utils.setTheme(mode);
        mCurThemeMode = mode;
        mEditor.putInt(Utils.THEME_KEY, mode);
        mEditor.apply();
    }

}
