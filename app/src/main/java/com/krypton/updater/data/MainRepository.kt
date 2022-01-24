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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import java.util.Date

@Singleton
class MainRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val updateChecker: UpdateChecker,
) {

    private val savedStateDataStore = context.savedStateDataStore

    val systemBuildDate: Date = Date(DeviceInfo.getBuildDate() * 1000)

    val systemBuildVersion: String = DeviceInfo.getBuildVersion()

    fun getLastCheckedTime(): Flow<Date> =
        savedStateDataStore.data.map {
            Date(it.lastCheckedTime)
        }

    private val _updateInfo = MutableStateFlow<Result<UpdateInfo>?>(null)
    val updateInfo: StateFlow<Result<UpdateInfo>?> = _updateInfo

    /**
     * Check for updates in github. The fetch result as a [Result] of type [UpdateInfo]
     * will be emitted from [updateInfo]. [UpdateInfo.Type] will indicate whether
     * there is a new update or not.
     */
    suspend fun fetchUpdateInfo() {
        val result = withContext(Dispatchers.IO) {
            updateChecker.checkForUpdate()
        }
        savedStateDataStore.updateData {
            it.toBuilder()
                .setLastCheckedTime(System.currentTimeMillis())
                .build()
        }
        _updateInfo.emit(result)
    }
}