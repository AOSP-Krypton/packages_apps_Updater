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

import dagger.hilt.android.lifecycle.HiltViewModel

import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.Inject

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mainRepository: MainRepository,
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

    val updateAvailable: LiveData<Boolean>
        get() = Transformations.map(updateInfo) { it.type == UpdateInfo.Type.NEW_UPDATE }

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
                bytesToBigFormat(
                    size
                )
            }
        }

    init {
        viewModelScope.launch {
            mainRepository.lastCheckedTime.collect {
                _lastCheckedTime.value = if (it.time > 0) TIME_FORMAT.format(it) else null
            }
        }
        viewModelScope.launch {
            mainRepository.getUpdateInfo().collect {
                _updateInfo.value = it
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
            val result = mainRepository.fetchUpdateInfo()
            _isCheckingForUpdate.value = false
            if (!result.first) _updateFailedEvent.value = Event(result.second?.message)
        }
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val BUILD_DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy")

        @SuppressLint("SimpleDateFormat")
        private val TIME_FORMAT = SimpleDateFormat("dd/MM/yy h:mm a")

        private val byteSizeArray = arrayOf("KiB", "MiB", "GiB")

        private fun bytesToBigFormat(bytes: Long): String {
            var convertedBytes = bytes
            val kib = DataUnit.KIBIBYTES.toBytes(1)
            if ((convertedBytes / kib) == 0L) return "$convertedBytes ${byteSizeArray[0]}"
            for (i in 0..3) {
                convertedBytes /= kib
                if (convertedBytes < kib) {
                    return if (i < byteSizeArray.size) "$convertedBytes ${byteSizeArray[i]}" else ""
                }
            }
            return ""
        }
    }
}