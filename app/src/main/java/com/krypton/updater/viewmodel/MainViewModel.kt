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

import android.annotation.SuppressLint
import android.util.DataUnit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.Event
import com.krypton.updater.data.MainRepository
import com.krypton.updater.data.UpdateInfo
import com.krypton.updater.data.download.DownloadRepository
import com.krypton.updater.data.update.UpdateRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.Inject

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mainRepository: MainRepository,
    private val downloadRepository: DownloadRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _updateInfo = MutableLiveData<UpdateInfo>()
    val updateInfo: LiveData<UpdateInfo>
        get() = _updateInfo

    private val _updateFailedEvent = MutableLiveData<Event<String?>>()
    val updateFailedEvent: LiveData<Event<String?>>
        get() = _updateFailedEvent

    val systemBuildDate: String = BUILD_DATE_FORMAT.format(mainRepository.systemBuildDate)

    val systemBuildVersion: String = mainRepository.systemBuildVersion

    private val _lastCheckedTime = MutableLiveData<String?>()
    val lastCheckedTime: LiveData<String?>
        get() = _lastCheckedTime

    private var updateCheckJob: Job? = null

    private val _isCheckingForUpdate = MutableLiveData(false)
    val isCheckingForUpdate: LiveData<Boolean>
        get() = _isCheckingForUpdate

    private val _newUpdateAvailable = MutableLiveData<Boolean>()
    val newUpdateAvailable: LiveData<Boolean>
        get() = _newUpdateAvailable

    private val _noUpdateAvailable = MutableLiveData<Boolean>()
    val noUpdateAvailable: LiveData<Boolean>
        get() = _noUpdateAvailable

    val updateVersion: LiveData<String?>
        get() = Transformations.map(updateInfo) { it.buildInfo?.version }

    val updateDate: LiveData<String?>
        get() = Transformations.map(updateInfo) {
            it.buildInfo?.date?.let { date ->
                BUILD_DATE_FORMAT.format(
                    Date(date)
                )
            }
        }

    val updateSize: LiveData<String?>
        get() = Transformations.map(updateInfo) {
            it.buildInfo?.fileSize?.let { size ->
                formatBytes(size)
            }
        }

    private val _allowUpdateCheck = MutableLiveData(true)
    val allowUpdateCheck: LiveData<Boolean>
        get() = _allowUpdateCheck

    init {
        viewModelScope.launch {
            mainRepository.lastCheckedTime.collect {
                _lastCheckedTime.value = if (it.time > 0) TIME_FORMAT.format(it) else null
            }
        }
        viewModelScope.launch {
            mainRepository.getUpdateInfo().collect {
                _updateInfo.value = it
                _newUpdateAvailable.value = it.type == UpdateInfo.Type.NEW_UPDATE
                _noUpdateAvailable.value = it.type == UpdateInfo.Type.NO_UPDATE
            }
        }
        viewModelScope.launch {
            downloadRepository.downloadState.combine(
                updateRepository.updateState
            ) { downloadState, updateState ->
                !downloadState.waiting &&
                        !downloadState.downloading &&
                        !updateState.initializing &&
                        !updateState.updating
            }.collect {
                _allowUpdateCheck.value = it
            }
        }
    }

    /**
     * Check for updates and post result in [updateInfo] (if successful) or
     * [updateFailedEvent] (if failed).
     */
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
            if (result.isFailure) _updateFailedEvent.value =
                Event(result.exceptionOrNull()?.message)
        }
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val BUILD_DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy")

        @SuppressLint("SimpleDateFormat")
        private val TIME_FORMAT = SimpleDateFormat("dd/MM/yy h:mm a")

        private val units = arrayOf("KiB", "MiB", "GiB")
        private val singleDecimalFmt = DecimalFormat("00.0")
        private val doubleDecimalFmt = DecimalFormat("0.00")
        private val KiB: Long = DataUnit.KIBIBYTES.toBytes(1)

        fun formatBytes(bytes: Long): String {
            val unit: String
            var rate = (bytes / KiB).toFloat()
            var i = 0
            while (true) {
                rate /= KiB
                if (rate >= 0.9f && rate < 1) {
                    unit = units[i + 1]
                    break
                } else if (rate < 0.9f) {
                    rate *= KiB
                    unit = units[i]
                    break
                }
                i++
            }
            val formattedSize = when {
                rate < 10 -> doubleDecimalFmt.format(rate)
                rate < 100 -> singleDecimalFmt.format(rate)
                rate < 1000 -> rate.toInt().toString()
                else -> rate.toString()
            }
            return "$formattedSize $unit"
        }


    }
}