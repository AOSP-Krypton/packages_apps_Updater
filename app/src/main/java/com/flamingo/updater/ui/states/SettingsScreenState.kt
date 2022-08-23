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

package com.flamingo.updater.ui.states

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.settings.SettingsRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class SettingsScreenState(
    private val coroutineScope: CoroutineScope,
    private val mainRepository: MainRepository,
    private val settingsRepository: SettingsRepository
) {

    val updateCheckInterval: Flow<Int>
        get() = settingsRepository.updateCheckInterval

    val optOutIncremental: Flow<Boolean>
        get() = settingsRepository.optOutIncremental

    val exportDownload: Flow<Boolean>
        get() = settingsRepository.exportDownload

    val autoReboot: Flow<Boolean>
        get() = settingsRepository.autoReboot

    val autoRebootDelay: Flow<Long>
        get() = settingsRepository.autoRebootDelay

    fun setUpdateCheckInterval(interval: Int) {
        coroutineScope.launch {
            settingsRepository.setUpdateCheckInterval(interval)
            mainRepository.setRecheckAlarm(interval)
        }
    }

    fun setOptOutIncremental(optOut: Boolean) {
        coroutineScope.launch {
            settingsRepository.setOptOutIncremental(optOut)
        }
    }

    fun setExportDownload(export: Boolean) {
        coroutineScope.launch {
            settingsRepository.setExportDownload(export)
        }
    }

    fun setAutoReboot(autoReboot: Boolean) {
        coroutineScope.launch {
            settingsRepository.setAutoReboot(autoReboot)
        }
    }

    fun setAutoRebootDelay(delay: Long) {
        coroutineScope.launch {
            settingsRepository.setAutoRebootDelay(delay)
        }
    }
}

@Composable
fun rememberSettingsScreenState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    mainRepository: MainRepository = get(),
    settingsRepository: SettingsRepository = get()
) = remember(coroutineScope, mainRepository, settingsRepository) {
    SettingsScreenState(
        coroutineScope = coroutineScope,
        mainRepository = mainRepository,
        settingsRepository = settingsRepository
    )
}