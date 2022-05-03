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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log

import com.krypton.updater.R
import com.krypton.updater.data.HashVerifier

import dagger.hilt.android.qualifiers.ApplicationContext

import java.io.File
import java.net.URL

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val jobScheduler: JobScheduler by lazy {
        context.getSystemService(JobScheduler::class.java)
    }

    private val downloadServiceComponent =
        ComponentName(context.packageName, context.getString(R.string.download_service))
    private val retryInterval =
        context.resources.getInteger(R.integer.minimum_download_retry_interval).toLong()

    private val cacheDir = context.cacheDir

    private var downloadInfo: DownloadInfo? = null

    var downloadFile: File? = null
        private set
    val downloadFileName: String?
        get() = downloadFile?.name

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState>
        get() = _downloadState

    val eventChannel = Channel<DownloadResult>()
    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float>
        get() = _progressFlow

    /**
     * Initiates the download by scheduling a job with [JobScheduler].
     *
     * @param downloadInfo a [DownloadInfo] object containing the details of
     *   the file to download.
     */
    fun download(downloadInfo: DownloadInfo) {
        logD("download, downloadInitiated = ${downloadState.value}")
        if (downloadState.value is DownloadState.Downloading) return
        _downloadState.value = DownloadState.Idle
        _progressFlow.value = 0f
        this.downloadInfo = downloadInfo
        try {
            val result = jobScheduler.schedule(buildJobInfo(downloadInfo))
            if (result == JobScheduler.RESULT_SUCCESS) {
                _downloadState.value = DownloadState.Waiting
                logD("Download scheduled successfully")
            } else {
                Log.e(TAG, "Failed to schedule download")
            }
        } catch (e: IllegalArgumentException) {
            Log.wtf(TAG, "Download service does not exist")
        }
    }

    /**
     * Creates a new [DownloadWorker] and starts it.
     *
     * @param downloadInfo a [Bundle] containing the information
     *   necessary to download the file.
     */
    suspend fun runWorker(downloadInfo: Bundle) {
        logD("runWorker, downloadState = ${downloadState.value}")
        _downloadState.value = DownloadState.Downloading

        downloadFile = File(cacheDir, downloadInfo.getString(DownloadInfo.FILE_NAME)!!)

        val urlResult = runCatching {
            URL(downloadInfo.getString(DownloadInfo.URL))
        }
        if (urlResult.isFailure) {
            val exception = urlResult.exceptionOrNull()
            Log.e(TAG, "Failed to open url", exception)
            eventChannel.send(DownloadResult.Failure(exception))
            _downloadState.value = DownloadState.Failed(exception)
            return
        }
        val fileSize = downloadInfo.getLong(DownloadInfo.FILE_SIZE)
        val sha512 = downloadInfo.getString(DownloadInfo.SHA_512)!!
        val downloadWorker = DownloadWorker(
            downloadFile!!,
            urlResult.getOrThrow(),
            fileSize,
            sha512,
        )

        coroutineScope {
            launch(Dispatchers.Main) {
                logD("listening to channel from worker")
                for (progress in downloadWorker.channel) {
                    _progressFlow.emit(progress)
                }
            }
            launch {
                logD("starting worker")
                val result = downloadWorker.run()
                if (result is DownloadResult.Success) {
                    _downloadState.value = DownloadState.Finished
                } else if (result is DownloadResult.Failure) {
                    Log.e(TAG, "Download failed", result.exception)
                    _downloadState.value = DownloadState.Failed(result.exception)
                }
                logD("sending result $result")
                withContext(Dispatchers.Main) {
                    eventChannel.send(result)
                }
            }
        }
    }

    /**
     * Cancels the download if any in progress.
     */
    fun cancelDownload() {
        logD("cancelDownload, downloadState = ${downloadState.value}")
        if (downloadState.value is DownloadState.Waiting ||
            downloadState.value is DownloadState.Downloading
        ) {
            jobScheduler.cancel(JOB_ID)
            downloadInfo = null
            _downloadState.value = DownloadState.Idle
        }
    }

    /**
     * Resets the manager to initial state.
     */
    suspend fun reset() {
        _progressFlow.emit(0f)
        _downloadState.emit(DownloadState.Idle)
        downloadFile = null
        downloadInfo = null
    }

    private fun buildJobInfo(downloadInfo: DownloadInfo) =
        JobInfo.Builder(JOB_ID, downloadServiceComponent)
            .setTransientExtras(buildExtras(downloadInfo))
            .setBackoffCriteria(retryInterval, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(downloadInfo.size, JobInfo.NETWORK_BYTES_UNKNOWN.toLong())
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

    private fun buildExtras(downloadInfo: DownloadInfo) =
        Bundle(4).apply {
            putString(DownloadInfo.URL, downloadInfo.url)
            putString(DownloadInfo.FILE_NAME, downloadInfo.name)
            putString(DownloadInfo.SHA_512, downloadInfo.sha512)
            putLong(DownloadInfo.FILE_SIZE, downloadInfo.size)
        }

    suspend fun restoreDownloadState(
        name: String,
        size: Long,
        sha512: String,
    ) {
        logD("restoring state, name = $name, size = $size, sha512 = $sha512")
        val file = File(cacheDir, name)
        if (file.isFile) {
            if (file.length() != size) {
                Log.w(TAG, "File size does not match, deleting")
                file.delete()
                return
            }
            val hashMatch = withContext(Dispatchers.IO) {
                HashVerifier.verifyHash(file, sha512)
            }
            if (!hashMatch) {
                Log.w(TAG, "File hash does not match, deleting")
                file.delete()
                return
            } else {
                logD("updating state")
                downloadFile = file
                _downloadState.emit(DownloadState.Finished)
                _progressFlow.emit(100f)
            }
        }
    }

    companion object {
        private const val JOB_ID = 2568346

        private const val TAG = "DownloadManager"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}