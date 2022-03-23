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

package com.krypton.updater.data.download

import android.util.DataUnit
import android.util.Log

import com.krypton.updater.data.HashVerifier

import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

import javax.net.ssl.HttpsURLConnection

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A worker whose job is to download the file from the given url.
 *
 * @property downloadFile the file to which the content should be downloaded to.
 * @property url the url to downloaded from.
 * @property fileSize the size of the file in bytes.
 * @property fileHash the SHA-512 hash of the file.
 */
class DownloadWorker(
    private val downloadFile: File,
    private val url: URL,
    private val fileSize: Long,
    private val fileHash: String,
) {
    private var downloadedBytes = 0L

    val channel = Channel<Long>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Run the worker.
     *
     * @return a [DownloadResult] indicating whether the work failed,
     *   or whether we should retry, or whether it was a success.
     */
    suspend fun run(): DownloadResult {
        logD("run")
        var resume = downloadFile.isFile
        if (resume) {
            downloadedBytes = downloadFile.length()
            logD("downloadedBytes = $downloadedBytes")
            if (downloadedBytes == fileSize) {
                logD("file already downloaded, verifying hash")
                if (HashVerifier.verifyHash(downloadFile, fileHash)) {
                    channel.send(downloadedBytes)
                    return DownloadResult.success()
                }
                Log.w(TAG, "File is corrupt, deleting")
                downloadFile.delete()
                downloadedBytes = 0
                resume = false
            }
        }
        logD("resume = $resume")
        val connectionResult = withContext(Dispatchers.IO) {
            openConnection(if (resume) "$downloadedBytes-" else null)
        }
        if (connectionResult.isFailure) {
            return DownloadResult.failure(connectionResult.exceptionOrNull())
        }
        val connection = connectionResult.getOrThrow()
        logD("connection opened")
        val downloadResult = withContext(Dispatchers.IO) {
            runCatching {
                FileOutputStream(downloadFile, resume).use { outStream ->
                    logD("output stream opened")
                    connection.inputStream.use { inStream ->
                        logD("input stream opened")
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var bytesRead = inStream.read(buffer)
                        while (currentCoroutineContext().isActive && bytesRead > 0) {
                            outStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            channel.send(downloadedBytes)
                            bytesRead = inStream.read(buffer)
                        }
                        logD("isActive = ${currentCoroutineContext().isActive}")
                        outStream.flush()
                    }
                }
                logD("streams closed, download finished")
                connection.disconnect()
                logD("connection disconnected")
            }
        }
        channel.close()
        logD("channel closed")
        if (downloadResult.isFailure) {
            return DownloadResult.failure(downloadResult.exceptionOrNull())
        }
        return if (downloadedBytes == fileSize) {
            if (HashVerifier.verifyHash(downloadFile, fileHash))
                DownloadResult.success()
            else
                DownloadResult.failure(Throwable("SHA-512 hash doesn't match. Possible download corruption!"))
        } else {
            DownloadResult.retry()
        }
    }

    private suspend fun openConnection(range: String?): Result<HttpsURLConnection> {
        val connectionResult: Result<HttpsURLConnection>? =
            withTimeoutOrNull(CONNECTION_RETRY_TIMEOUT) {
                while (isActive) {
                    val openResult = runCatching {
                        url.openConnection() as HttpsURLConnection
                    }
                    if (openResult.isSuccess) {
                        val conn = openResult.getOrThrow()
                        if (range != null) {
                            conn.setRequestProperty("Range", "bytes=$range")
                        }
                        val responseCode = conn.responseCode
                        if (responseCode == HttpsURLConnection.HTTP_OK ||
                            responseCode == HttpsURLConnection.HTTP_CREATED ||
                            responseCode == HttpsURLConnection.HTTP_ACCEPTED ||
                            responseCode == HttpsURLConnection.HTTP_PARTIAL
                        ) {
                            Result.success(conn)
                        } else {
                            Log.e(TAG, "Connection failed with response code $responseCode")
                        }
                    }
                }
                Result.failure(Throwable("Current download job was cancelled"))
            }
        return connectionResult
            ?: Result.failure(Throwable("Timeout while establishing connection, retry"))
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private val DOWNLOAD_BUFFER_SIZE = DataUnit.KIBIBYTES.toBytes(256).toInt()

        private val CONNECTION_RETRY_TIMEOUT = TimeUnit.SECONDS.toMillis(5)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}