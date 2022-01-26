/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.data.update

import android.net.Uri
import android.util.Log

import java.util.zip.ZipFile

/**
 * Factory class to generate [PayloadInfo]
 */
object PayloadInfoFactory {
    private const val TAG = "PayloadInfoFactory"
    private const val DEBUG = false

    // Metadata file path in the zip file
    private const val METADATA_FILE = "META-INF/com/android/metadata"

    // File name of payload
    private const val PAYLOAD_FILE_NAME = "payload.bin"

    private val PAYLOAD_OFFSET_REGEX = Regex("$PAYLOAD_FILE_NAME:[0-9]+:[0-9]+")

    // Text file containing header info
    private const val PAYLOAD_PROPERTIES_FILE = "payload_properties.txt"

    /**
     * Generate a [PayloadInfo] with information parsed
     * from the given uri (file:// type). Should not be called
     * from main thread.
     *
     * @param uri the uri to parse information from
     * @return a [Result] of [PayloadInfo].
     */
    fun createPayloadInfo(uri: Uri): Result<PayloadInfo> {
        logD("uri = $uri")
        val filePath = uri.toString()
        val result = runCatching {
            ZipFile(uri.path).use { zipFile ->
                val offsetSizePair = getOffsetAndSize(zipFile)
                    ?: return Result.failure(Exception("Could not retrieve offset and size"))
                val headerKeyValuePairs = getHeaderKeyValuePairs(zipFile)
                    ?: return Result.failure(Exception("Could not retrieve header key value pairs"))
                return Result.success(
                    PayloadInfo(
                        filePath,
                        offsetSizePair.first,
                        offsetSizePair.second,
                        headerKeyValuePairs
                    )
                )
            }
        }
        return Result.failure(
            result.exceptionOrNull() ?: Exception("Failed to generate payload info")
        )
    }

    private fun getOffsetAndSize(zipFile: ZipFile): Pair<Long, Long>? {
        val metadata = zipFile.getEntry(METADATA_FILE) ?: return null
        val result = runCatching {
            zipFile.getInputStream(metadata).bufferedReader().use { reader ->
                val line = reader.readLine() ?: return null
                logD("metadata line = $line")
                val offsetSizeString = PAYLOAD_OFFSET_REGEX.find(line)?.value ?: return null
                val list = offsetSizeString.split(':')
                return Pair(list[1].toLong(), list[2].toLong())
            }
        }
        Log.e(TAG, "Extracting offset and size failed", result.exceptionOrNull())
        return null
    }

    private fun getHeaderKeyValuePairs(zipFile: ZipFile): Array<String>? {
        val payloadProps = zipFile.getEntry(PAYLOAD_PROPERTIES_FILE) ?: return null
        val result = runCatching {
            zipFile.getInputStream(payloadProps).bufferedReader().use { reader ->
                val fileContent = reader.readLines().filter { it.isNotBlank() }
                if (fileContent.size != 4) return null
                return fileContent.toTypedArray()
            }
        }
        Log.e(TAG, "Extracting offset and size failed", result.exceptionOrNull())
        return null
    }

    private fun logD(msg: String) {
        if (DEBUG) Log.d(TAG, msg)
    }
}