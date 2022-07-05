/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.data.download

import android.content.Context
import android.os.Bundle
import android.util.Log

import com.flamingo.updater.data.BuildInfo
import com.flamingo.updater.data.FileExportManager
import com.flamingo.updater.data.FileCopyStatus
import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.savedStateDataStore
import com.flamingo.updater.data.settings.appSettingsDataStore

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val applicationScope: CoroutineScope,
    private val downloadManager: DownloadManager,
    private val fileExportManager: FileExportManager,
    appDatabase: AppDatabase,
) {

    private val savedStateDatastore = context.savedStateDataStore
    private val appSettingsDatastore = context.appSettingsDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

    val downloadState: StateFlow<DownloadState>
        get() = downloadManager.downloadState

    val downloadFileName: String?
        get() = downloadManager.downloadFileName

    private val _restoringDownloadState = MutableStateFlow(false)
    val restoringDownloadState: StateFlow<Boolean>
        get() = _restoringDownloadState

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
                downloadSource,
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

    suspend fun resetState() {
        downloadManager.reset()
    }

    fun clearCache() {
        applicationScope.launch {
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