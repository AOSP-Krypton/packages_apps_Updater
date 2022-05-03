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

import androidx.lifecycle.ViewModel

import com.krypton.updater.data.BuildInfo
import com.krypton.updater.data.download.DownloadRepository
import com.krypton.updater.data.download.DownloadState
import com.krypton.updater.data.Event
import com.krypton.updater.data.FileCopyStatus

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val downloadState: StateFlow<DownloadState>
        get() = downloadRepository.downloadState

    val downloadFailedEvent: Flow<Event<String?>>
        get() = downloadRepository.downloadState.filterIsInstance<DownloadState.Failed>().map {
            Event(it.exception?.localizedMessage)
        }

    val downloadProgress: StateFlow<Float>
        get() = downloadRepository.downloadProgressFlow

    val fileCopyStatus: Channel<FileCopyStatus>
        get() = downloadRepository.fileCopyStatus

    val restoringDownloadState: StateFlow<Boolean>
        get() = downloadRepository.restoringDownloadState

    fun startDownload(buildInfo: BuildInfo, source: String?) {
        downloadRepository.triggerDownload(buildInfo, source)
    }

    fun cancelDownload() {
        downloadRepository.cancelDownload()
    }
}