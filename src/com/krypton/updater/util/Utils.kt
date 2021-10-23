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

package com.krypton.updater.util

import android.os.Environment
import android.os.SystemProperties
import android.util.Log
import android.view.View

import com.krypton.updater.util.Constants.MB

import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Date

import kotlin.jvm.JvmStatic

// TODO : remove all JvmStatic annotations once entire app is in kotlin
class Utils {
    companion object {
        private const val TAG = "UpdaterUtils"

        // Build props
        private const val PROP_DEVICE = "ro.krypton.build.device"
        private const val PROP_VERSION = "ro.krypton.build.version"
        private const val PROP_DATE = "ro.build.date.utc"

        // Date format (Ex: 12 June 2021, 11:59 AM)
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a")

        // Downloads directory as a File object
        // TODO : deal with this deprecated API
        private val DOWNLOADS_DIR = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS)

        // Get device code name
        @JvmStatic
        fun getDevice() = SystemProperties.get(PROP_DEVICE, "unavailable")

        // Get build version
        @JvmStatic
        fun getVersion() = SystemProperties.get(PROP_VERSION, "unavailable")

        // Get build date (milliseconds since epoch)
        @JvmStatic
        fun getBuildDate(): Long = SystemProperties.get(PROP_DATE, "0").toLong() * 1000L

        // Format given time in milliseconds with dateFormat
        @JvmStatic
        fun formatDate(date: Long) = DATE_FORMAT.format(Date(date))

        /* Set visibility to all views in @param views
         * View.VISIBLE or View.GONE based on @param visible
         */
        @JvmStatic
        fun setVisible(
            visible: Boolean,
            vararg views: View,
        ) {
            val flag = if (visible) View.VISIBLE else View.GONE
            views.forEach { it.setVisibility(flag) }
        }

        // Get a File object pointing to the @param fileName
        // file in Downloads folder
        @JvmStatic
        fun getDownloadFile(fileName: String) = File(DOWNLOADS_DIR, fileName)

        // Calculate md5 hash of the given file
        @JvmStatic
        fun computeMd5(file: File): String? {
            val md5Digest: MessageDigest
            try {
                md5Digest = MessageDigest.getInstance("MD5")
            } catch (e: NoSuchAlgorithmException) {
                return null
            }
            // Files processed will be of GB order usually, so 1MB buffer will speed up the process
            val buffer = ByteArray(MB)
            var bytesRead: Int
            try {
                file.inputStream().use {
                    bytesRead = it.read(buffer)
                    while (bytesRead != -1) {
                        md5Digest.update(buffer, 0, bytesRead)
                        bytesRead = it.read(buffer)
                    }
                }
                return String(md5Digest.digest())
            } catch (e: IOException) {
                Log.e(TAG, "IOException while computing md5 of file ${file.getAbsolutePath()}")
            }
            return null
        }

        // Reads the content from a url and returns a string representation of it
        // TODO : remove this function and inline url.readText() where it's used
        @JvmStatic
        fun parseRawContent(url: URL): String? = url.readText()
    }
}
