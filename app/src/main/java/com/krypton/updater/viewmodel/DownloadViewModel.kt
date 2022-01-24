/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krypton.updater.data.BuildInfo

import com.krypton.updater.data.download.DownloadRepository
import com.krypton.updater.data.download.DownloadState

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

    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int>
        get() = _downloadProgress

    init {
        viewModelScope.launch {
            downloadRepository.downloadState.collect {
                _downloadState.value = it
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadProgressFlow.collect {
                val totalSize = downloadRepository.downloadSize
                if (totalSize > 0) {
                    _downloadProgress.value = ((it * 100) / totalSize).toInt()
                } else {
                    _downloadProgress.value = 0
                }
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