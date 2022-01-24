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

import android.os.Bundle
import com.krypton.updater.data.BuildInfo

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadManager: DownloadManager,
) {

    val downloadState: StateFlow<DownloadState>
        get() = downloadManager.downloadState

    val downloadEventChannel: Channel<DownloadResult>
        get() = downloadManager.eventChannel

    val downloadProgressFlow: StateFlow<Long>
        get() = downloadManager.progressFlow

    val downloadSize: Long
        get() = downloadManager.downloadSize

    val downloadFileName: String?
        get() = downloadManager.downloadFileName

    /**
     * Schedule a download via [DownloadManager]
     *
     * @param buildInfo a [BuildInfo] object containing the details of
     *   the file to download.
     */
    fun triggerDownload(buildInfo: BuildInfo) {
        downloadManager.download(buildInfo)
    }

    /**
     * Start the download.
     *
     * @param downloadConfig a [Bundle] containing the information
     *   necessary to download the file.
     */
    suspend fun startDownload(downloadConfig: Bundle) {
        downloadManager.runWorker(downloadConfig)
    }

    /**
     * Cancel any ongoing download.
     */
    fun cancelDownload() {
        downloadManager.cancelDownload()
    }
}