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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.Event
import com.krypton.updater.data.MainRepository
import com.krypton.updater.data.UpdateInfo

import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mainRepository: MainRepository,
): ViewModel() {

    private val _updateLiveData = MutableLiveData<UpdateInfo>()
    val updateLiveData: LiveData<UpdateInfo> = _updateLiveData

    private val _updateFailedEventLiveData = MutableLiveData<Event<String?>>()
    val updateFailedEventLiveData: LiveData<Event<String?>> = _updateFailedEventLiveData

    /**
     * Check for updates and post result in [updateLiveData] (if successful) or
     * [updateFailedEventLiveData] (if failed).
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            val result = mainRepository.getUpdateInfo()
            if (result.isSuccess) {
                _updateLiveData.value = result.getOrThrow()
            } else {
                _updateFailedEventLiveData.value = Event(result.exceptionOrNull()?.message)
            }
        }
    }
}