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

package com.krypton.updater.data

import android.os.Bundle

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadManager: DownloadManager,
) {

    val downloadEventChannel: Channel<DownloadResult> = downloadManager.eventChannel
    val downloadProgressFlow: StateFlow<Long> = downloadManager.progressFlow

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
     * Get size of the file downloading.
     *
     * @return size of the download file if any, 0 otherwise.
     */
    fun getDownloadSize(): Long = downloadManager.downloadSize

    /**
     * Get name of the file downloading.
     *
     * @return the name of the download file if any, null otherwise.
     */
    fun getDownloadFileName(): String? = downloadManager.downloadFileName

    /**
     * Cancel any ongoing download.
     */
    fun cancelDownload() {
        downloadManager.cancelDownload()
    }
}