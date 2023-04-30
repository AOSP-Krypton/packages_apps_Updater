/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
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

package com.kosp.updater.data.update

import android.content.Context
import android.net.Uri
import android.util.Log

import com.kosp.updater.R

import java.util.zip.ZipFile

class PayloadInfo private constructor(
    val filePath: String,
    val offset: Long,
    val size: Long,
    val headerKeyValuePairs: Array<String>,
) {
    object Factory {
        private const val TAG = "PayloadInfoFactory"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

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
        fun createPayloadInfo(context: Context, uri: Uri): Result<PayloadInfo> {
            logD("uri = $uri")
            return runCatching {
                ZipFile(uri.path).use { zipFile ->
                    val offsetSizePairResult = getOffsetAndSize(context, zipFile)
                    if (offsetSizePairResult.isFailure) {
                        return Result.failure(
                            offsetSizePairResult.exceptionOrNull()
                                ?: Throwable(context.getString(R.string.failed_to_retrieve_offset_and_size))
                        )
                    }
                    val headerKeyValuePairResult = getHeaderKeyValuePairs(context, zipFile)
                    if (headerKeyValuePairResult.isFailure) {
                        return Result.failure(
                            headerKeyValuePairResult.exceptionOrNull()
                                ?: Throwable(context.getString(R.string.failed_to_retrieve_header_key_value_pair))
                        )
                    }
                    val offsetSizePair = offsetSizePairResult.getOrThrow()
                    PayloadInfo(
                        uri.toString(),
                        offsetSizePair.first,
                        offsetSizePair.second,
                        headerKeyValuePairResult.getOrThrow()
                    )
                }
            }
        }

        private fun getOffsetAndSize(context: Context, zipFile: ZipFile): Result<Pair<Long, Long>> {
            val metadata = zipFile.getEntry(METADATA_FILE)
                ?: return Result.failure(Throwable(context.getString(R.string.zip_does_not_contain_metadata_file)))
            return runCatching {
                zipFile.getInputStream(metadata).bufferedReader().use { reader ->
                    val line =
                        reader.readLine()
                            ?: return Result.failure(Throwable(context.getString(R.string.metadata_file_empty)))
                    logD("metadata line = $line")
                    val offsetSizeString = PAYLOAD_OFFSET_REGEX.find(line)?.value
                        ?: return Result.failure(Throwable(context.getString(R.string.failed_to_extract_offset_and_size)))
                    val list = offsetSizeString.split(':')
                    Pair(list[1].toLong(), list[2].toLong())
                }
            }
        }

        private fun getHeaderKeyValuePairs(
            context: Context,
            zipFile: ZipFile
        ): Result<Array<String>> {
            val payloadProps = zipFile.getEntry(PAYLOAD_PROPERTIES_FILE) ?: return Result.failure(
                Throwable(context.getString(R.string.zip_file_does_not_contain_payload_properties))
            )
            return runCatching {
                zipFile.getInputStream(payloadProps).bufferedReader().use { reader ->
                    val fileContent = reader.readLines().filter { it.isNotBlank() }
                    if (fileContent.size != 4) return Result.failure(
                        Throwable(context.getString(R.string.payload_properties_file_does_not_have_key_value_pairs))
                    )
                    fileContent.toTypedArray()
                }
            }
        }

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}