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

package com.flamingo.updater.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.flamingo.updater.data.BuildInfo
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.download.DownloadState
import com.flamingo.updater.data.FileCopyStatus

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow

import javax.inject.Inject

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val downloadState: StateFlow<DownloadState>
        get() = downloadRepository.downloadState

    val downloadFailedEventChannel = Channel<String?>(1, BufferOverflow.DROP_OLDEST)

    val fileCopyStatus: Channel<FileCopyStatus>
        get() = downloadRepository.fileCopyStatus

    val restoringDownloadState: StateFlow<Boolean>
        get() = downloadRepository.restoringDownloadState

    init {
        viewModelScope.launch {
            downloadRepository.downloadState.filterIsInstance<DownloadState.Failed>().collect {
                downloadFailedEventChannel.send(it.exception?.localizedMessage)
            }
        }
    }

    fun startDownload(buildInfo: BuildInfo, source: String) {
        downloadRepository.triggerDownload(buildInfo, source)
    }

    fun cancelDownload() {
        downloadRepository.cancelDownload()
    }
}