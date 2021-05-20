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

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.krypton.updater.R;
import com.krypton.updater.util.Utils;

public class SettingsActivity extends AppCompatActivity {

    protected static final int REQUEST_CODE_SELECT_LOCATION = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.updater_settings_title);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(android.R.id.content, new SettingsFragment(), null)
                .commit();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent resultData) {
        if (requestCode == REQUEST_CODE_SELECT_LOCATION
                && resultCode == RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                if (uri != null) {
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .edit()
                        .putString(Utils.DOWNLOAD_LOCATION_KEY,
                            uri.getLastPathSegment().replace("primary:", "/sdcard/"))
                        .apply();
                }
            }
        }
    }
}
