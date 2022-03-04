/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.krypton.updater.data

import android.os.SystemProperties

object DeviceInfo {
    private const val PROP_DEVICE = "ro.krypton.build.device"
    private const val PROP_DATE = "ro.build.date.utc"
    private const val PROP_VERSION = "ro.krypton.build.version"
    private const val PROP_BUILD_VERSION_INCREMENTAL = "ro.build.version.incremental"

    /**
     * Get device code name.
     */
    fun getDevice(): String = SystemProperties.get(PROP_DEVICE, "Unknown")

    /**
     * Get build date as unix timestamp (milliseconds since epoch).
     */
    fun getBuildDate(): Long = SystemProperties.get(PROP_DATE, "0").toLong() * 1000 /* convert to millis */

    /**
     * Get build version.
     */
    fun getBuildVersion(): String = SystemProperties.get(PROP_VERSION, "0.0")

    /**
     * Get current incremental build version.
     */
    fun getBuildVersionIncremental(): Long = SystemProperties.get(PROP_BUILD_VERSION_INCREMENTAL, "0").toLong()
}