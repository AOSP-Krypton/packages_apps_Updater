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
    val downloadSize: Long
        get() = downloadInfo?.size ?: 0

    private val _downloadState = MutableStateFlow(DownloadState.idle())
    val downloadState: StateFlow<DownloadState>
        get() = _downloadState

    val eventChannel = Channel<DownloadResult>()
    private val _progressFlow = MutableStateFlow<Long>(0)
    val progressFlow: StateFlow<Long>
        get() = _progressFlow

    /**
     * Initiates the download by scheduling a job with [JobScheduler].
     *
     * @param downloadInfo a [DownloadInfo] object containing the details of
     *   the file to download.
     */
    fun download(downloadInfo: DownloadInfo) {
        logD("download, downloadInitiated = ${downloadState.value}")
        if (downloadState.value.downloading) return
        _downloadState.value = DownloadState.idle()
        _progressFlow.value = 0
        this.downloadInfo = downloadInfo
        try {
            val result = jobScheduler.schedule(buildJobInfo(downloadInfo))
            if (result == JobScheduler.RESULT_SUCCESS) {
                _downloadState.value = DownloadState.waiting()
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
        _downloadState.value = DownloadState.downloading()

        downloadFile = File(cacheDir, downloadInfo.getString(DownloadInfo.FILE_NAME)!!)

        val urlResult = runCatching {
            URL(downloadInfo.getString(DownloadInfo.URL))
        }
        if (urlResult.isFailure) {
            eventChannel.send(DownloadResult.failure(urlResult.exceptionOrNull()))
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
                for (size in downloadWorker.channel) {
                    _progressFlow.emit(size)
                }
            }
            launch {
                logD("starting worker")
                val result = downloadWorker.run()
                if (result.isSuccess) {
                    _downloadState.value = DownloadState.finished()
                } else {
                    _downloadState.value = DownloadState.failed(result.exceptionOrNull())
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
        if (downloadState.value.waiting || downloadState.value.downloading) {
            jobScheduler.cancel(JOB_ID)
            downloadInfo = null
            _downloadState.value = DownloadState.idle()
        }
    }

    /**
     * Resets the manager to initial state.
     */
    suspend fun reset() {
        _progressFlow.emit(0)
        _downloadState.emit(DownloadState.idle())
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

    suspend fun restoreDownloadState(downloadInfo: DownloadInfo) {
        logD("restoring state, buildInfo = $downloadInfo")
        val file = File(cacheDir, downloadInfo.name)
        if (file.isFile) {
            if (file.length() != downloadInfo.size) {
                Log.w(TAG, "file size does not match, deleting")
                file.delete()
                return
            }
            val hashMatch = withContext(Dispatchers.IO) {
                HashVerifier.verifyHash(file, downloadInfo.sha512)
            }
            if (!hashMatch) {
                Log.w(TAG, "file hash does not match, deleting")
                file.delete()
                return
            } else {
                logD("updating state")
                this.downloadInfo = downloadInfo
                downloadFile = file
                _downloadState.emit(DownloadState.finished())
                _progressFlow.emit(downloadInfo.size)
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