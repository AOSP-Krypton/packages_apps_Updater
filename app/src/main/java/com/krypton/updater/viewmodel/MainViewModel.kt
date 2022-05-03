/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.MainRepository
import com.krypton.updater.data.UpdateInfo
import com.krypton.updater.data.download.DownloadRepository
import com.krypton.updater.data.update.UpdateRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import java.util.Date

import javax.inject.Inject

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mainRepository: MainRepository,
    private val downloadRepository: DownloadRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    val updateInfo: Flow<UpdateInfo>
        get() = mainRepository.getUpdateInfo()

    val systemBuildDate: Date
        get() = mainRepository.systemBuildDate

    val systemBuildVersion: String = mainRepository.systemBuildVersion

    val lastCheckedTime: Flow<Long>
        get() = mainRepository.lastCheckedTime.map { it.time }

    private var updateCheckJob: Job? = null

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean>
        get() = _isCheckingForUpdate

    val updateAvailable: Flow<Boolean>
        get() = mainRepository.getUpdateInfo().map { it.type == UpdateInfo.Type.NEW_UPDATE }

    val updateResultAvailable: Flow<Boolean>
        get() = mainRepository.getUpdateInfo().map {
            it.type == UpdateInfo.Type.NEW_UPDATE || it.type == UpdateInfo.Type.NO_UPDATE
        }

    val updateFailedEvent = Channel<String?>(2, BufferOverflow.DROP_OLDEST)

    fun checkForUpdates() {
        updateCheckJob?.let {
            if (it.isActive) it.cancel()
        }
        updateCheckJob = viewModelScope.launch {
            _isCheckingForUpdate.value = true
            downloadRepository.resetState()
            updateRepository.resetState()
            val result = mainRepository.fetchUpdateInfo()
            _isCheckingForUpdate.value = false
            if (result.isFailure) {
                updateFailedEvent.send(result.exceptionOrNull()?.localizedMessage)
            }
        }
    }
}