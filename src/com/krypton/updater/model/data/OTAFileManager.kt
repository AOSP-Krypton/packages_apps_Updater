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

package com.krypton.updater.model.data

import android.net.Uri
import android.os.Environment
import android.os.FileUtils
import android.os.FileUtils.S_IRWXU
import android.os.FileUtils.S_IRWXG
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OTAFileManager @Inject constructor() {

    private val otaPackageDir: File
    private val otaFile: File

    init {
        otaPackageDir = File(Environment.getDataDirectory(), OTA_DIR)
        checkOTADir()
        otaFile = File(otaPackageDir, UPDATE_FILE)
    }

    fun getOTAFileUri() = Uri.fromFile(otaFile)

    fun copyToOTAPackageDir(inStream: InputStream): Boolean {
        if (!cleanup()) {
            return false
        }
        try {
            FileOutputStream(otaFile).use {
                FileUtils.copy(inStream, it)
                val errno: Int = FileUtils.setPermissions(otaFile, S_IRWXU or S_IRWXG, -1, -1)
                if (errno == 0) {
                    return true
                } else {
                    Log.e(TAG, "setPermissions for ${otaFile.absolutePath} failed with errno $errno")
                }
            }
        } catch(ex: IOException) {
            Log.e(TAG, "IOException when copying to ota dir, ${ex.message}")
        }
        return false
    }

    private fun checkOTADir() {
        if (!otaPackageDir.isDirectory()) {
            throw RuntimeException("ota package dir ${otaPackageDir.absolutePath} does not exist")
        }
        if (!(otaPackageDir.canRead() &&
                otaPackageDir.canWrite() && otaPackageDir.canExecute())) {
            throw RuntimeException("no rwx permission for ${otaPackageDir.absolutePath}")
        }
    }

    private fun cleanup(): Boolean {
        otaPackageDir.listFiles()?.forEach {
            if (!it.delete()) {
                Log.e(TAG, "deleting ${it.absolutePath} failed")
                return false
            }
        }
        return true
    }

    companion object {
        private const val TAG = "OTAFileManager"
        private const val OTA_DIR = "kosp_ota"
        private const val UPDATE_FILE = "update.zip"
    }
}
