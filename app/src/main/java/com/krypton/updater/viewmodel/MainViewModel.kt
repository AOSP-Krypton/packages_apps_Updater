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
import androidx.lifecycle.*

import com.krypton.updater.data.Event
import com.krypton.updater.data.MainRepository
import com.krypton.updater.data.UpdateInfo

import dagger.hilt.android.lifecycle.HiltViewModel

import java.text.SimpleDateFormat

import javax.inject.Inject

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.*

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

    val updateVersion: LiveData<String>
        get() = Transformations.map(updateInfo) { it.buildInfo.version }

    val updateDate: LiveData<String>
        get() = Transformations.map(updateInfo) { BUILD_DATE_FORMAT.format(Date(it.buildInfo.date)) }

    val updateSize: LiveData<String>
        get() = Transformations.map(updateInfo) { bytesToBigFormat(it.buildInfo.fileSize) }

    init {
        viewModelScope.launch {
            mainRepository.getLastCheckedTime().collect {
                _lastCheckedTime.value = if (it.time > 0) TIME_FORMAT.format(it) else null
            }
        }
        viewModelScope.launch {
            mainRepository.updateInfo.filterNotNull().collect {
                if (it.isSuccess) {
                    val data = it.getOrThrow()
                    _updateInfo.value = data
                } else {
                    _updateFailedEvent.value = Event(it.exceptionOrNull()?.message)
                }
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
            mainRepository.fetchUpdateInfo()
            _isCheckingForUpdate.value = false
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