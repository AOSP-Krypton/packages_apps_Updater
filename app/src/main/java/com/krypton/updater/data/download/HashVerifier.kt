/*
 * Copyright (C) 2022 AOSP-Krypton Project
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

package com.krypton.updater.data.download

import android.util.DataUnit
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object HashVerifier {
    private const val TAG = "HashVerifier"
    private val HASH_BUFFER_SIZE = DataUnit.MEBIBYTES.toBytes(1).toInt()

    private fun computeHash(inStream: InputStream): String? {
        val messageDigest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(HASH_BUFFER_SIZE)
        try {
            var bytesRead = inStream.read(buffer)
            while (bytesRead > 0) {
                messageDigest.update(buffer, 0, bytesRead)
                bytesRead = inStream.read(buffer)
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException while computing hash, ${e.message}")
            return null
        }
        val builder = StringBuilder()
        messageDigest.digest().forEach {
            builder.append(String.format("%02x", it))
        }
        return builder.toString()
    }

    fun verifyHash(file: File, hash: String): Boolean {
        if (!file.isFile) return false
        return try {
            FileInputStream(file).use { computeHash(it) == hash }
        } catch (e: IOException) {
            Log.e(TAG, "IOException while computing hash, ${e.message}")
            false
        }
    }

    fun verifyHash(firstFileInputStream: InputStream, secondFileInputStream: InputStream): Boolean {
        return try {
            computeHash(firstFileInputStream) == computeHash(secondFileInputStream)
        } catch (e: IOException) {
            Log.e(TAG, "IOException while computing hash, ${e.message}")
            false
        }
    }
}