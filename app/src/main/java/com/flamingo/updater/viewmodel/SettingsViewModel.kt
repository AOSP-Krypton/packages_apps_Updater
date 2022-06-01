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

import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.settings.SettingsRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mainRepository: MainRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val updateCheckInterval: Flow<Int>
        get() = settingsRepository.updateCheckInterval

    val optOutIncremental: Flow<Boolean>
        get() = settingsRepository.optOutIncremental

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

    /**
     * Set whether to opt out of incremental updates.
     *
     * @param optOut true if opting out, false otherwise.
     */
    fun setOptOutIncremental(optOut: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOptOutIncremental(optOut)
        }
    }
}