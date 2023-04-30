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

import android.content.Context
import android.os.Bundle
import android.util.Log

import com.kosp.updater.data.BuildInfo
import com.kosp.updater.data.FileExportManager
import com.kosp.updater.data.FileCopyStatus
import com.kosp.updater.data.room.AppDatabase
import com.kosp.updater.data.savedStateDataStore
import com.kosp.updater.data.settings.appSettingsDataStore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadRepository(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val fileExportManager: FileExportManager,
    appDatabase: AppDatabase,
    applicationScope: CoroutineScope
) {

    private val savedStateDatastore = context.savedStateDataStore
    private val appSettingsDatastore = context.appSettingsDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

    val downloadState: StateFlow<DownloadState>
        get() = downloadManager.downloadState

    val downloadFileName: String?
        get() = downloadManager.downloadFileName

    private val _restoringDownloadState = MutableStateFlow(false)
    val restoringDownloadState: StateFlow<Boolean> = _restoringDownloadState.asStateFlow()

    val fileCopyStatus = Channel<FileCopyStatus>(Channel.CONFLATED)

    init {
        applicationScope.launch {
            delay(1000)
            restoreDownloadState()
            downloadState.collect { state ->
                saveDownloadState(state)
                val exportDownload = appSettingsDatastore.data.map { it.exportDownload }.first()
                if (state is DownloadState.Finished && exportDownload) {
                    exportFile()
                }
            }
        }
    }

    private suspend fun exportFile() {
        downloadManager.downloadFile?.let { file ->
            fileCopyStatus.send(FileCopyStatus.Copying)
            val result = withContext(Dispatchers.IO) {
                fileExportManager.copyToExportDir(file)
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
     * @param downloadSource the mirror to download from.
     */
    fun triggerDownload(buildInfo: BuildInfo, downloadSource: String) {
        downloadManager.download(
            DownloadInfo(
                buildInfo.downloadSources[downloadSource]!!,
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
        withContext(Dispatchers.IO) {
            downloadManager.runWorker(downloadConfig)
        }
    }

    /**
     * Cancel any ongoing download.
     */
    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    private suspend fun saveDownloadState(state: DownloadState) {
        logD("saveStateToDatabase")
        if (state !is DownloadState.Finished) return
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
        withContext(Dispatchers.IO) {
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

    fun resetState() {
        downloadManager.reset()
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach {
                it.delete()
            }
        }
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