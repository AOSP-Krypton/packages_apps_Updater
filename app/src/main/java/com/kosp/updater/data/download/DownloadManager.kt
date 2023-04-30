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

package com.kosp.updater.data.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log

import com.kosp.updater.R
import com.kosp.updater.data.util.verifyHash

import java.io.File
import java.net.URL

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DownloadManager(context: Context) {

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

        downloadFile = File(cacheDir, downloadInfo.getString(DownloadInfo.FILE_NAME)!!)

        val urlResult = runCatching {
            URL(downloadInfo.getString(DownloadInfo.URL))
        }
        if (urlResult.isFailure) {
            val exception = urlResult.exceptionOrNull()
            Log.e(TAG, "Failed to open url", exception)
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
        logD("starting worker")
        downloadWorker.run {
            _downloadState.value = it
            if (it is DownloadState.Failed) {
                Log.e(TAG, "Download failed", it.exception)
            }
        }
    }

    /**
     * Cancels the download if any in progress.
     */
    fun cancelDownload() {
        logD("cancelDownload, downloadState = ${downloadState.value}")
        if (downloadState.value is DownloadState.Waiting ||
            downloadState.value is DownloadState.Downloading ||
            downloadState.value is DownloadState.Retry
        ) {
            jobScheduler.cancel(JOB_ID)
            downloadInfo = null
            _downloadState.value = DownloadState.Idle
        }
    }

    /**
     * Resets the manager to initial state.
     */
    fun reset() {
        _downloadState.value = DownloadState.Idle
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
            if (!verifyHash(file, sha512)) {
                Log.w(TAG, "File hash does not match, deleting")
                file.delete()
                return
            } else {
                logD("updating state")
                downloadFile = file
                _downloadState.emit(DownloadState.Finished)
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