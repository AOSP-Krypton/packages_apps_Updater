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

package com.krypton.updater.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.BuildInfo
import com.krypton.updater.data.download.DownloadRepository
import com.krypton.updater.data.download.DownloadState
import com.krypton.updater.data.Event

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _downloadState = MutableLiveData<DownloadState>()
    val downloadState: LiveData<DownloadState>
        get() = _downloadState

    private val _downloadFailedEvent = MutableLiveData<Event<String?>>()
    val downloadFailed: LiveData<Event<String?>>
        get() = _downloadFailedEvent

    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int>
        get() = _downloadProgress

    val isDownloading: Boolean
        get() = _downloadState.value?.let { it.waiting || it.downloading } ?: false

    private val _stateRestoreFinished = MutableLiveData<Boolean>()
    val stateRestoreFinished: LiveData<Boolean>
        get() = _stateRestoreFinished

    private val _exportingFile = MutableLiveData<Boolean>()
    val exportingFile: LiveData<Boolean>
        get() = _exportingFile

    private val _exportingFailed = MutableLiveData<Event<String?>>()
    val exportingFailed: LiveData<Event<String?>>
        get() = _exportingFailed

    init {
        viewModelScope.launch {
            downloadRepository.exportingFile.collect {
                _exportingFile.value = it
            }
        }
        viewModelScope.launch {
            for (result in downloadRepository.fileExportResult) {
                if (result.isFailure) _exportingFailed.value = Event(result.exceptionOrNull()?.message)
            }
        }
        viewModelScope.launch {
            downloadRepository.stateRestoreFinished.collect {
                _stateRestoreFinished.value = it
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadState.collect {
                _downloadState.value = it
                if (it.failed) {
                    _downloadFailedEvent.value = Event(it.exception?.message)
                }
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadProgressFlow.collect {
                val totalSize = downloadRepository.downloadSize
                _downloadProgress.value = if (totalSize > 0) ((it * 100) / totalSize).toInt() else 0
            }
        }
    }

    fun startDownload(buildInfo: BuildInfo) {
        downloadRepository.triggerDownload(buildInfo)
    }

    fun cancelDownload() {
        downloadRepository.cancelDownload()
    }
}