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

package com.krypton.updater;

import android.os.SystemProperties;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Date;
import java.text.SimpleDateFormat;

public class Utils {

    private static final String TAG = "Utils";

    public static final String SHARED_PREFS = "com.krypton.updater.shared_prefs";
    public static final String THEME_KEY = "theme_settings_preference";

    private static final String PROP_DEVICE = "ro.krypton.build.device";
    private static final String PROP_VERSION = "ro.krypton.build.version";
    private static final String PROP_TIMESTAMP = "ro.krypton.build.timestamp";

    public static final String BUILD_INFO_SOURCE_URL = "https://raw.githubusercontent.com/AOSP-Krypton/official_devices_ota/A11/";
    public static final String BUILD_INFO = "build-info";
    public static final String BUILD_VERSION = "version";
    public static final String BUILD_TIMESTAMP = "timestamp";
    public static final String BUILD_NAME = "filename";

    public static BuildInfo buildInfo;

    public static String getDevice() {
        return SystemProperties.get(PROP_DEVICE, "unavailable");
    }

    public static String getVersion() {
        return SystemProperties.get(PROP_VERSION, "unavailable");
    }

    public static String getTimestamp() {
        return SystemProperties.get(PROP_TIMESTAMP, "unavailable");
    }

    public static boolean checkBuildStatus(String version, String timestamp) {
        float currVersion = Float.parseFloat(getVersion().substring(1));
        float newVersion = Float.parseFloat(version.substring(1));
        if (newVersion > currVersion) {
            return true;
        }
        try {
            Date currDate = parseDate(getTimestamp());
            Date newDate = parseDate(timestamp);
            if (newDate.after(currDate)) {
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    public static Date parseDate(String timestamp) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd/hh:mm");
        return formatter.parse(timestamp);
    }

    public static void setTheme(int mode) {
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
    }

    public static class BuildInfo {
        private String version;
        private String timestamp;
        private String filename;

        public BuildInfo(String vn, String ts, String fn) {
            version = vn;
            timestamp = ts;
            filename = fn;
        }

        public String getVersion() {
            return version;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getFileName() {
            return filename;
        }
    }

}
