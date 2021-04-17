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

import android.content.res.Resources;
import android.os.SystemProperties;

import com.krypton.updater.R;

public class Utils {

    private static final String PROP_DEVICE = "ro.krypton.build.device";
    private static final String PROP_VERSION = "ro.krypton.build.version";
    private static final String PROP_TIMESTAMP = "ro.krypton.build.timestamp";

    public static String getDevice(Resources res) {
        StringBuilder builder = new StringBuilder(res.getString(R.string.device_name_text));
        builder.append(": ");
        builder.append(SystemProperties.get(PROP_DEVICE, "unavailable"));
        return builder.toString();
    }

    public static String getVersion(Resources res) {
        StringBuilder builder = new StringBuilder(res.getString(R.string.version_text));
        builder.append(": ");
        builder.append(SystemProperties.get(PROP_VERSION, "unavailable"));
        return builder.toString();
    }

    public static String getTimestamp(Resources res) {
        StringBuilder builder = new StringBuilder(res.getString(R.string.timestamp_text));
        builder.append(": ");
        builder.append(SystemProperties.get(PROP_TIMESTAMP, "unavailable"));
        return builder.toString();
    }

}
