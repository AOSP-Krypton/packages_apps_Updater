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
import com.krypton.updater.data.BuildInfo

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

    private var buildInfo: BuildInfo? = null
    private var jobInfo: JobInfo? = null
    private var jobExtras: Bundle? = null

    private var downloadFile: File? = null
    val downloadFileName: String?
        get() = downloadFile?.name
    val downloadSize: Long
        get() = buildInfo?.fileSize ?: 0

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
     * @param buildInfo a [BuildInfo] object containing the details of
     *   the file to download.
     */
    fun download(buildInfo: BuildInfo) {
        logD("download, downloadInitiated = ${downloadState.value}")
        if (downloadState.value.downloading) return
        _downloadState.value = DownloadState.idle()
        _progressFlow.value = 0
        if (this.buildInfo != buildInfo) {
            this.buildInfo = buildInfo
            jobExtras = buildExtras(buildInfo)
            jobInfo = buildJobInfo(buildInfo, jobExtras!!)
        }
        if (jobScheduler.schedule(jobInfo!!) == JobScheduler.RESULT_SUCCESS) {
            _downloadState.value = DownloadState.waiting()
            logD("download scheduled successfully")
        } else {
            logD("failed to schedule download")
        }
    }

    /**
     * Creates a new [DownloadWorker] and starts it.
     *
     * @param downloadInfo a [Bundle] containing the information
     *   necessary to download the file.
     */
    suspend fun runWorker(downloadInfo: Bundle) {
        logD("runWorker, downloadState = $downloadState")
        _downloadState.value = DownloadState.downloading()

        downloadFile = File(cacheDir, downloadInfo.getString(BuildInfo.FILE_NAME)!!)

        val urlResult = runCatching {
            URL(downloadInfo.getString(BuildInfo.URL))
        }
        if (urlResult.isFailure) {
            eventChannel.send(DownloadResult.failure(urlResult.exceptionOrNull()))
            return
        }
        val fileSize = downloadInfo.getLong(BuildInfo.FILE_SIZE)
        val sha512 = downloadInfo.getString(BuildInfo.SHA_512)!!
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
                    _downloadState.value = DownloadState.failed()
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
        logD("cancelDownload, downloadState = $downloadState")
        if (downloadState.value.waiting || downloadState.value.downloading) {
            jobScheduler.cancel(JOB_ID)
            buildInfo = null
            jobExtras = null
            jobInfo = null
            _downloadState.value = DownloadState.idle()
        }
    }

    /**
     * Resets the manager to initial state.
     * Deletes any completed download as well.
     */
    suspend fun reset() {
        _progressFlow.emit(0)
        _downloadState.emit(DownloadState.idle())
        downloadFile?.delete()
        downloadFile = null
        buildInfo = null
        jobInfo = null
        jobExtras = null
    }

    private fun buildJobInfo(buildInfo: BuildInfo, jobExtras: Bundle) =
        JobInfo.Builder(JOB_ID, downloadServiceComponent)
            .setTransientExtras(jobExtras)
            .setBackoffCriteria(retryInterval, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(buildInfo.fileSize, JobInfo.NETWORK_BYTES_UNKNOWN.toLong())
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

    private fun buildExtras(buildInfo: BuildInfo) =
        Bundle(4).apply {
            putString(BuildInfo.URL, buildInfo.url)
            putString(BuildInfo.FILE_NAME, buildInfo.fileName)
            putString(BuildInfo.SHA_512, buildInfo.sha512)
            putLong(BuildInfo.FILE_SIZE, buildInfo.fileSize)
        }

    suspend fun restoreDownloadState(buildInfo: BuildInfo) {
        logD( "restoring state, buildInfo = $buildInfo")
        val file = File(cacheDir, buildInfo.fileName)
        if (file.isFile) {
            if (file.length() != buildInfo.fileSize) {
                logD("file size does not match, deleting")
                file.delete()
                return
            }
            val hashMatch = withContext(Dispatchers.IO) {
                DownloadWorker.checkFileIntegrity(file, buildInfo.sha512)
            }
            if (!hashMatch) {
                logD("file hash does not match, deleting")
                file.delete()
                return
            } else {
                logD("updating state")
                this.buildInfo = buildInfo
                downloadFile = file
                _downloadState.emit(DownloadState.finished())
                _progressFlow.emit(buildInfo.fileSize)
            }
        }
    }

    companion object {
        private const val JOB_ID = 2568346

        private const val TAG = "DownloadManager"
        private const val DEBUG = false

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}