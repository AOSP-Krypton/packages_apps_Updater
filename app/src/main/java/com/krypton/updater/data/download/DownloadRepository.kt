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
import com.krypton.updater.data.room.AppDatabase
import com.krypton.updater.data.savedStateDataStore

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadManager: DownloadManager,
    @ApplicationContext context: Context,
    private val applicationScope: CoroutineScope,
    private val fileCopier: FileCopier,
    appDatabase: AppDatabase,
) {

    private val savedStateDatastore = context.savedStateDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

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

    // To prevent state flow from download manager overwriting
    // saved state in database.
    private val _stateRestoreFinished = MutableStateFlow(false)
    val stateRestoreFinished: StateFlow<Boolean>
        get() = _stateRestoreFinished

    private val _exportingFile = MutableStateFlow(false)
    val exportingFile: StateFlow<Boolean>
        get() = _exportingFile

    val fileExportResult = Channel<Result<Unit>>(2, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        applicationScope.launch {
            _stateRestoreFinished.value = restoreDownloadState()
        }
        applicationScope.launch {
            downloadState.collect {
                if (stateRestoreFinished.value) {
                    applicationScope.launch {
                        saveStateToDatabase(it)
                    }
                }
                if (it.finished) {
                    exportFile()
                }
            }
        }
    }

    private suspend fun exportFile() {
        downloadManager.downloadFile?.let { file ->
            _exportingFile.value = true
            fileExportResult.send(
                withContext(Dispatchers.IO) {
                    fileCopier.copyToExportDir(file)
                }
            )
            _exportingFile.value = false
        }
    }

    /**
     * Schedule a download via [DownloadManager]
     *
     * @param buildInfo a [BuildInfo] object containing the details of
     *   the file to download.
     */
    fun triggerDownload(buildInfo: BuildInfo) {
        downloadManager.download(
            DownloadInfo(
                buildInfo.url,
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

    private suspend fun saveStateToDatabase(state: DownloadState) {
        logD("saveStateToDatabase")
        if (!state.finished) return
        savedStateDatastore.updateData {
            it.toBuilder()
                .setDownloadFinished(true)
                .build()
        }
        logD("state saved")
    }

    private suspend fun restoreDownloadState(): Boolean {
        logD("restoreDownloadState")
        val downloadFinished = savedStateDatastore.data.map { it.downloadFinished }.first()
        if (!downloadFinished) {
            logD("no saved state available")
            return true
        }
        withContext(Dispatchers.Default) {
            val count = updateInfoDao.entityCount()
            if (count == 0) {
                logD("Update info database is empty")
                return@withContext
            }
            val buildInfoEntity =
                updateInfoDao.getBuildInfo().first() ?: return@withContext
            logD("restoring state")
            downloadManager.restoreDownloadState(
                DownloadInfo(
                    buildInfoEntity.url,
                    buildInfoEntity.fileName,
                    buildInfoEntity.fileSize,
                    buildInfoEntity.sha512,
                )
            )
        }
        return true
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