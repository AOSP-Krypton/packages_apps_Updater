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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    private var stateRestoreFinished = false

    val updateState: StateFlow<UpdateState>
        get() = updateManager.updateState

    val updateProgress: StateFlow<Float>
        get() = updateManager.progressFlow

    val isUpdating: Boolean
        get() = updateManager.isUpdating

    val isUpdatePaused: Boolean
        get() = updateManager.isUpdatePaused

    private val _readyForUpdate = MutableStateFlow(false)
    val readyForUpdate: StateFlow<Boolean>
        get() = _readyForUpdate

    val copyingFile = Channel<Boolean>(2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val copyResultChannel = Channel<Result<Unit>>(2, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        applicationScope.launch {
            restoreState()
        }
        applicationScope.launch {
            downloadManager.downloadState.collect {
                if (it.finished) {
                    downloadManager.downloadFile?.let { file ->
                        copyOTAFile(Uri.fromFile(file))
                    }
                } else if (it.idle && updateState.value.idle) {
                    _readyForUpdate.value = false
                }
            }
        }
        applicationScope.launch {
            updateState.collect {
                if (stateRestoreFinished) {
                    if (it.finished) saveUpdateFinishedState()
                }
            }
        }
    }

    suspend fun start() {
        withContext(Dispatchers.Default) {
            updateManager.start()
        }
    }

    suspend fun pause() {
        withContext(Dispatchers.Default) {
            updateManager.pause()
        }
    }

    suspend fun resume() {
        withContext(Dispatchers.Default) {
            updateManager.resume()
        }
    }

    suspend fun cancel() {
        withContext(Dispatchers.Default) {
            updateManager.cancel()
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
        copyingFile.send(true)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val result = otaFileManager.copyToOTAPackageDir(uri)
                if (result.isFailure) {
                    throw result.exceptionOrNull()!!
                }
            }
        }
        copyingFile.send(false)
        copyResultChannel.send(result)
        _readyForUpdate.value = result.isSuccess
    }

    fun resetState() {
        _readyForUpdate.value = false
        updateManager.reset()
    }

    private suspend fun restoreState() {
        val updateFinished =
            savedStateDataStore.data.map { it.updateFinished }.firstOrNull() == true
        if (updateFinished) {
            updateManager.restoreUpdateFinishedState()
        }
        stateRestoreFinished = true
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