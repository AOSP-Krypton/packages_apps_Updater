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

import android.content.Context
import android.os.Bundle
import android.util.Log

import com.krypton.updater.data.BuildInfo
import com.krypton.updater.data.FileCopier
import com.krypton.updater.data.FileCopyStatus
import com.krypton.updater.data.room.AppDatabase
import com.krypton.updater.data.savedStateDataStore

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadManager: DownloadManager,
    @ApplicationContext context: Context,
    applicationScope: CoroutineScope,
    private val fileCopier: FileCopier,
    appDatabase: AppDatabase,
) {

    private val savedStateDatastore = context.savedStateDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

    val downloadState: StateFlow<DownloadState>
        get() = downloadManager.downloadState

    val downloadEventChannel: Channel<DownloadResult>
        get() = downloadManager.eventChannel

    val downloadProgressFlow: StateFlow<Float>
        get() = downloadManager.progressFlow

    val downloadFileName: String?
        get() = downloadManager.downloadFileName

    private val _restoringDownloadState = MutableStateFlow(false)
    val restoringDownloadState: StateFlow<Boolean>
        get() = _restoringDownloadState

    val fileCopyStatus = Channel<FileCopyStatus>(2, BufferOverflow.DROP_OLDEST)

    init {
        applicationScope.launch {
            restoreDownloadState()
            downloadState.collect {
                saveDownloadState(it)
                if (it.finished) {
                    exportFile()
                }
            }
        }
    }

    private suspend fun exportFile() {
        downloadManager.downloadFile?.let { file ->
            fileCopyStatus.send(FileCopyStatus.Copying)
            val result = withContext(Dispatchers.IO) {
                fileCopier.copyToExportDir(file)
            }
            if (result.isSuccess) {
                fileCopyStatus.send(FileCopyStatus.Success)
            } else {
                fileCopyStatus.send(FileCopyStatus.Failure(result.exceptionOrNull()?.localizedMessage))
            }
        }
    }

    /**
     * Schedule a download via [DownloadManager]
     *
     * @param buildInfo a [BuildInfo] object containing the details of
     *   the file to download.
     * @param downloadSource the mirror to download from. If non empty,
     *   download url will be selected from [BuildInfo.downloadSources] map with
     *   [downloadSource] as key or else, defaults to [BuildInfo.url] field.
     */
    // TODO remove support for [BuildInfo.url] once we switch to A13
    fun triggerDownload(buildInfo: BuildInfo, downloadSource: String? = null) {
        downloadManager.download(
            DownloadInfo(
                downloadSource?.let { buildInfo.downloadSources!![it] } ?: buildInfo.url!!,
                buildInfo.fileName,
                buildInfo.fileSize,
                buildInfo.sha512
            )
        )
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

    private suspend fun saveDownloadState(state: DownloadState) {
        logD("saveStateToDatabase")
        if (!state.finished) return
        savedStateDatastore.updateData {
            it.toBuilder()
                .setDownloadFinished(true)
                .build()
        }
        logD("state saved")
    }

    private suspend fun restoreDownloadState() {
        logD("restoreDownloadState")
        _restoringDownloadState.value = true
        val downloadFinished = savedStateDatastore.data.map { it.downloadFinished }.first()
        if (!downloadFinished) {
            logD("no saved state available")
            _restoringDownloadState.value = false
            return
        }
        withContext(Dispatchers.Default) {
            if (updateInfoDao.entityCount() == 0) {
                logD("Update info database is empty")
                return@withContext
            }
            val buildInfoEntity = updateInfoDao.getBuildInfo().firstOrNull() ?: return@withContext
            logD("restoring state")
            downloadManager.restoreDownloadState(
                buildInfoEntity.fileName,
                buildInfoEntity.fileSize,
                buildInfoEntity.sha512,
            )
        }
        _restoringDownloadState.value = false
    }

    suspend fun resetState() {
        downloadManager.reset()
    }

    companion object {
        private const val TAG = "DownloadRepository"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}