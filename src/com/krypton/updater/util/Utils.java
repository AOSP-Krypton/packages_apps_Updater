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

package com.krypton.updater.util;

import android.os.SystemProperties;
import android.util.Log;

public final class Utils {

    private static final String TAG = "Utils";

    private static final String PROP_DEVICE = "ro.krypton.build.device";
    private static final String PROP_VERSION = "ro.krypton.build.version";
    private static final String PROP_TIMESTAMP = "ro.krypton.build.timestamp";

    public static final String BUILD_INFO = "build-info";
    public static final String BUILD_VERSION = "version";
    public static final String BUILD_TIMESTAMP = "timestamp";
    public static final String BUILD_NAME = "filename";
    public static final String BUILD_SIZE = "filesize";
    public static final String BUILD_MD5SUM = "md5sum";

    public static final String DOWNLOAD_LOCATION_KEY = "download_location_preference";
    public static final String DEFAULT_DOWNLOAD_LOC = "/sdcard/Download";

    // Restore download progress
    public static final String DOWNLOADED_SIZE = "downloadedSize";
    public static final String DOWNLOAD_STARTED = "downloadStarted";
    public static final String DOWNLOAD_PAUSED = "downloadPaused";
    public static final String DOWNLOAD_FINISHED = "downloadFinished";

    // Restore update engine progress
    public static final String LOCAL_UPGRADE_MODE = "localUpgradeMode";
    public static final String UPDATE_STARTED = "updateStarted";
    public static final String UPDATE_PAUSED = "updatePaused";
    public static final String UPDATE_FINISHED = "updateFinished";
    public static final String UPDATE_EXIT_CODE = "updateExitCode";
    public static final String UPDATE_STATUS = "updateStatus";
    public static final String UPDATE_PROGRESS = "updateProgress";

    // Error codes for apply update failure
    public static final int APPLY_PAYLOAD_FAILED = 101;
    public static final int FILE_INVALID = 102;

    // 1 MB in bytes
    public static final int MB = 1048576;

    public static String getDevice() {
        return SystemProperties.get(PROP_DEVICE, "unavailable");
    }

    public static String getVersion() {
        return SystemProperties.get(PROP_VERSION, "unavailable");
    }

    public static String getTimestamp() {
        return SystemProperties.get(PROP_TIMESTAMP, "unavailable");
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
}
