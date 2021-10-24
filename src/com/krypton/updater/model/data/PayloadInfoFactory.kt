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
import android.util.Log

import java.io.IOException
import java.util.zip.ZipFile

/*
 * Factory class to generate PayloadInfo object
 */
class PayloadInfoFactory private constructor() {
    companion object {
        private const val TAG = "PayloadInfoFactory"
        private const val DEBUG = false

        // Metadata file path in the zip file
        private const val METADATA_FILE = "META-INF/com/android/metadata"
        // File name of payload
        private const val PAYLOAD_FILE_NAME = "payload.bin"
        // Text file containing header info
        private const val PAYLOAD_PROPERTIES_FILE = "payload_properties.txt"

        /*
         * Returns a PayloadInfo object with information parsed
         * from the given @param uri (file:// type) of a file
         */
        // TODO : remove this annotation once everthing is in kotlin
        @JvmStatic
        fun createPayloadInfo(uri: Uri?) =
            PayloadInfo().also { payloadInfo ->
                logD("uri = $uri")
                uri?.let { uri ->
                    payloadInfo.filePath = uri.toString()
                    try {
                        ZipFile(uri.getPath()).use { zipFile ->
                            setOffsetAndSize(zipFile, payloadInfo)
                            setHeaderKeyValuePairs(zipFile, payloadInfo)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException while extracting payload info from $uri")
                    }
                }
            }

        // Internal method to set payload offset and size from the opened ZipFile
        private fun setOffsetAndSize(
            zipFile: ZipFile,
            payloadInfo: PayloadInfo
        ) {
            zipFile.getEntry(METADATA_FILE).let { metadata ->
                try {
                    zipFile.getInputStream(metadata).bufferedReader().use { reader ->
                        reader.readLine()?.let { line ->
                            logD("metadata line = $line")
                            val indexOfDelimiter = line.indexOf('=') + 1
                            logD("indexOfDelimiter = $indexOfDelimiter")
                            // Make sure the substring will be within the limits
                            if (indexOfDelimiter >= 0 && (line.length - indexOfDelimiter) > 0) {
                                line.substring(indexOfDelimiter).split(',')
                                    // Find the first string set containing offset and size of payload file if any
                                    .find { it.contains(PAYLOAD_FILE_NAME) }
                                    // split the string based on delimiter : and load the values in @param payloadInfo
                                    ?.let { offsetAndSizeString ->
                                        logD("offsetAndSizeString = $offsetAndSizeString")
                                        offsetAndSizeString.split(':').let {
                                            payloadInfo.offset = it.get(1).toLong()
                                            payloadInfo.size = it.get(2).toLong()
                                        }
                                    }
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "IOException while extracting payload offset and size from zip ${zipFile.getName()}")
                }
            }
        }

        // Internal method to set headerKeyValuePairs from the opened ZipFile
        private fun setHeaderKeyValuePairs(
            zipFile: ZipFile,
            payloadInfo: PayloadInfo,
        ) {
            zipFile.getEntry(PAYLOAD_PROPERTIES_FILE)?.let { payloadProps ->
                try {
                    zipFile.getInputStream(payloadProps).bufferedReader().use { reader ->
                        payloadInfo.headerKeyValuePairs =
                            Array<String?>(4) { reader.readLine() }
                    }
                    logD("headerKeyValuePairs = ${payloadInfo.headerKeyValuePairs}")
                } catch (e: IOException) {
                    Log.e(TAG, "IOException when extracting payload header info from zip ${zipFile.getName()}")
                }
            }
        }

        private fun logD(msg: String?) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}