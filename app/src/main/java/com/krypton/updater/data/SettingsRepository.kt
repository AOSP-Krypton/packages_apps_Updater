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

package com.krypton.updater.data

import android.content.Context

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appSettings = context.appSettings

    val updateCheckInterval: Flow<Int>
        get() = appSettings.data.map { it.updateCheckInterval }

    val optOutIncremental: Flow<Boolean>
        get() = appSettings.data.map { it.optOutIncremental }

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
}