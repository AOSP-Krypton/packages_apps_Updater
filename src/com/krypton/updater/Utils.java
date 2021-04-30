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
import android.util.Log;

import java.util.Date;
import java.text.SimpleDateFormat;

public final class Utils {

    private static final String TAG = "Utils";

    // Message constants
    public static final String MESSAGE = "com.krypton.updater.MESSAGE";
    public static final int APP_IN_BACKGROUND = 1;
    public static final int APP_IN_FOREGROUND = 2;
    public static final int FAILED_TO_UPDATE_BUILD_INFO = 3;
    public static final int FETCH_BUILD_INFO = 4;
    public static final int SET_INITIAL_DOWNLOAD_PROGRESS = 5;
    public static final int FINISHED_DOWNLOAD = 6;
    public static final int NO_INTERNET = 7;
    public static final int NO_NEW_BUILD_FOUND = 8;
    public static final int START_DOWNLOAD = 9;
    public static final int UPDATED_BUILD_INFO = 10;
    public static final int UPDATE_DOWNLOADED_SIZE = 11;
    public static final int UPDATE_PROGRESS_BAR = 12;
    public static final int RESTORE_STATUS = 13;
    public static final int PAUSE_DOWNLOAD = 14;
    public static final int RESUME_DOWNLOAD = 15;
    public static final int CANCEL_DOWNLOAD = 16;
    public static final int DELETE_DOWNLOAD = 17;

    public static final String SHARED_PREFS = "com.krypton.updater.shared_prefs";
    public static final String THEME_KEY = "theme_settings_preference";
    public static final String DOWNLOAD_LOCATION_KEY = "download_location_preference";
    public static final int REQUEST_CODE = 1219;

    private static final String PROP_DEVICE = "ro.krypton.build.device";
    private static final String PROP_VERSION = "ro.krypton.build.version";
    private static final String PROP_TIMESTAMP = "ro.krypton.build.timestamp";

    public static final String BUILD_INFO_SOURCE_URL = "https://raw.githubusercontent.com/AOSP-Krypton/official_devices_ota/A11/";
    public static final String BUILD_INFO = "build-info";
    public static final String BUILD_VERSION = "version";
    public static final String BUILD_TIMESTAMP = "timestamp";
    public static final String BUILD_NAME = "filename";
    public static final String BUILD_SIZE = "filesize";

    public static final String DOWNLOAD_SOURCE_URL = "https://sourceforge.net/projects/kosp/files/KOSP-A11-Releases/";
    public static final String DEFAULT_DOWNLOAD_LOC = "/sdcard/Download";
    public static final String DOWNLOADED_SIZE = "downloadedSize";
    public static final String DOWNLOAD_PAUSED = "downloadPaused";
    public static final String DOWNLOAD_FINISHED = "downloadFinished";

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

    public static void sleepThread(int duration) {
        try {
            Thread.sleep(duration);
        } catch (Exception e) {
            log(e);
        }
    }

    public static void log(String text, boolean val) {
        Log.d(TAG, String.format("%s: %b", text, val));
    }

    public static void log(String text, int val) {
        Log.d(TAG, String.format("%s: %d", text, val));
    }

    public static void log(String text, float val) {
        Log.d(TAG, String.format("%s: %f", text, val));
    }

    public static void log(String text, long val) {
        Log.d(TAG, String.format("%s: %d", text, val));
    }

    public static void log(Exception e) {
        Log.d(TAG, "caught exception", e);
    }

    public static void log(String str) {
        Log.d(TAG, str);
    }

    public static int convertToMB(long lenByte) {
        return (int) (lenByte/1048576);
    }

    public static String parseProgressText(int val, int size) {
        return String.format("%d/%d MB", val, size);
    }

    public static String parseProgressText(long val, long size) {
        return parseProgressText(convertToMB(val), convertToMB(size));
    }
}
