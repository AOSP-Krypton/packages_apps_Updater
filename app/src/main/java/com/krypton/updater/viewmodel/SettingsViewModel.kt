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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.MainRepository
import com.krypton.updater.data.SettingsRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mainRepository: MainRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _updateCheckInterval = MutableLiveData<Int>()
    val updateCheckInterval: LiveData<Int>
        get() = _updateCheckInterval

    init {
        viewModelScope.launch {
            settingsRepository.updateCheckInterval.collect {
                _updateCheckInterval.value = it
            }
        }
    }

    /**
     * Set interval (in days) for automatic update checking.
     *
     * @param interval the number of days after which update
     *      availability should be checked in the background.
     */
    fun setUpdateCheckInterval(interval: Int) {
        viewModelScope.launch {
            settingsRepository.setUpdateCheckInterval(interval)
            mainRepository.setRecheckAlarm(interval)
        }
    }
}