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
    private static final String PROP_DATE = "ro.build.date.utc";

    public static String getDevice() {
        return SystemProperties.get(PROP_DEVICE, "unavailable");
    }

    public static String getVersion() {
        return SystemProperties.get(PROP_VERSION, "unavailable");
    }

    public static String getBuildDate() {
        return SystemProperties.get(PROP_DATE, "unavailable");
    }

    public static void sleepThread(int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
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
