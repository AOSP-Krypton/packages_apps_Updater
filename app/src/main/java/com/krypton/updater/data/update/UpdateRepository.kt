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

package com.krypton.updater.data.update

import android.content.Context
import android.net.Uri

import com.krypton.updater.data.FileCopyStatus
import com.krypton.updater.data.download.DownloadManager
import com.krypton.updater.data.savedStateDataStore

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val applicationScope: CoroutineScope,
    private val updateManager: UpdateManager,
    private val otaFileManager: OTAFileManager,
    private val downloadManager: DownloadManager,
) {

    private val savedStateDataStore = context.savedStateDataStore

    val updateState: StateFlow<UpdateState>
        get() = updateManager.updateState

    val isUpdating: Boolean
        get() = updateManager.isUpdating

    val isUpdatePaused: Boolean
        get() = updateManager.isUpdatePaused

    private val _readyForUpdate = MutableStateFlow(false)
    val readyForUpdate: StateFlow<Boolean>
        get() = _readyForUpdate

    val fileCopyStatus = Channel<FileCopyStatus>(2, BufferOverflow.DROP_OLDEST)

    val supportsUpdateSuspension: Boolean
        get() = updateManager.supportsUpdateSuspension

    init {
        applicationScope.launch {
            downloadManager.downloadState.collect {
                if (it.finished) {
                    downloadManager.downloadFile?.let { file ->
                        copyOTAFile(Uri.fromFile(file))
                    }
                } else if (it.idle && updateState.value is UpdateState.Idle) {
                    _readyForUpdate.value = false
                }
            }
        }
        applicationScope.launch {
            updateState.collect {
                if (it is UpdateState.Finished) saveUpdateFinishedState()
            }
        }
    }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            updateManager.start()
        }
    }

    suspend fun pause() {
        withContext(Dispatchers.IO) {
            updateManager.pause()
        }
    }

    suspend fun resume() {
        withContext(Dispatchers.IO) {
            updateManager.resume()
        }
    }

    suspend fun cancel() {
        withContext(Dispatchers.IO) {
            updateManager.cancel()
        }
    }

    suspend fun reboot() {
        withContext(Dispatchers.IO) {
            updateManager.reboot()
        }
    }

    /**
     * Prepare for local upgrade.
     *
     * @param uri the [Uri] of the update zip file.
     */
    suspend fun copyOTAFile(uri: Uri) {
        updateManager.reset()
        clearSavedUpdateState()
        _readyForUpdate.value = false
        fileCopyStatus.send(FileCopyStatus.Copying)
        val result = withContext(Dispatchers.IO) {
            otaFileManager.copyToOTAPackageDir(uri)
        }
        if (result.isSuccess) {
            fileCopyStatus.send(FileCopyStatus.Success)
        } else {
            fileCopyStatus.send(FileCopyStatus.Failure(result.exceptionOrNull()?.localizedMessage))
        }
        _readyForUpdate.value = result.isSuccess
    }

    fun resetState() {
        _readyForUpdate.value = false
        updateManager.reset()
    }

    private fun saveUpdateFinishedState() {
        applicationScope.launch {
            savedStateDataStore.updateData {
                it.toBuilder()
                    .setUpdateFinished(true)
                    .build()
            }
        }
    }

    private fun clearSavedUpdateState() {
        applicationScope.launch {
            savedStateDataStore.updateData {
                it.toBuilder()
                    .clearUpdateFinished()
                    .build()
            }
        }
    }
}