/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.data.settings

import android.content.Context

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(context: Context) {

    private val appSettings = context.appSettingsDataStore

    val updateCheckInterval: Flow<Int> = appSettings.data.map { it.updateCheckInterval }

    val optOutIncremental: Flow<Boolean> = appSettings.data.map { it.optOutIncremental }

    val exportDownload: Flow<Boolean> = appSettings.data.map { it.exportDownload }

    val autoReboot: Flow<Boolean> = appSettings.data.map { it.autoReboot }

    val autoRebootDelay: Flow<Long> = appSettings.data.map { it.autoRebootDelay }

    /**
     * Set interval (in days) for automatic update checking.
     *
     * @param interval the number of days after which update
     *      availability should be checked in the background.
     */
    suspend fun setUpdateCheckInterval(interval: Int) {
        appSettings.updateData {
            it.toBuilder()
                .setUpdateCheckInterval(interval)
                .build()
        }
    }

    /**
     * Set whether to opt out of incremental updates.
     *
     * @param optOut true if opting out, false otherwise.
     */
    suspend fun setOptOutIncremental(optOut: Boolean) {
        appSettings.updateData {
            it.toBuilder()
                .setOptOutIncremental(optOut)
                .build()
        }
    }

    suspend fun setExportDownload(export: Boolean) {
        appSettings.updateData {
            it.toBuilder()
                .setExportDownload(export)
                .build()
        }
    }

    suspend fun setAutoReboot(autoReboot: Boolean) {
        appSettings.updateData {
            it.toBuilder()
                .setAutoReboot(autoReboot)
                .build()
        }
    }

    suspend fun setAutoRebootDelay(delay: Long) {
        appSettings.updateData {
            it.toBuilder()
                .setAutoRebootDelay(delay)
                .build()
        }
    }
}