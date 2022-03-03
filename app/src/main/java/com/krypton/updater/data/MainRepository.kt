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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

import com.krypton.updater.data.room.AppDatabase
import com.krypton.updater.data.room.BuildInfoEntity
import com.krypton.updater.data.room.ChangelogEntity
import com.krypton.updater.services.PeriodicUpdateCheckerService

import dagger.hilt.android.qualifiers.ApplicationContext

import java.util.concurrent.TimeUnit
import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MainRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    appDatabase: AppDatabase,
    private val updateChecker: UpdateChecker,
) {

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(AlarmManager::class.java)
    }

    private val savedStateDatastore = context.savedStateDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

    val systemBuildDate = Date(DeviceInfo.getBuildDate())

    val systemBuildVersion: String = DeviceInfo.getBuildVersion()

    val lastCheckedTime: Flow<Date>
        get() = savedStateDatastore.data.map { Date(it.lastCheckedTime) }

    fun getUpdateInfo(): Flow<UpdateInfo> {
        return updateInfoDao.getBuildInfo().combine(
            updateInfoDao.getChangelogs()
        ) { buildInfoEntity, changelogs ->
            val buildInfo = buildInfoEntity?.let {
                BuildInfo(
                    it.version,
                    it.date,
                    it.url,
                    it.fileName,
                    it.fileSize,
                    it.sha512,
                )
            }
            UpdateInfo(
                buildInfo,
                changelogs,
                type = if (buildInfo == null) {
                    UpdateInfo.Type.UNKNOWN
                } else {
                    if (UpdateChecker.isNewUpdate(buildInfo)) {
                        UpdateInfo.Type.NEW_UPDATE
                    } else {
                        UpdateInfo.Type.NO_UPDATE
                    }
                }
            )
        }
    }

    /**
     * Check for updates in github.
     *
     * @return a [Result] representing whether fetch was success or not.
     */
    suspend fun fetchUpdateInfo(): Result<Unit> {
        deleteSavedUpdateInfo()
        val result = withContext(Dispatchers.IO) {
            updateChecker.checkForUpdate()
        }
        savedStateDatastore.updateData {
            it.toBuilder()
                .setLastCheckedTime(System.currentTimeMillis())
                .build()
        }
        return if (result.isSuccess) {
            result.getOrThrow()?.let { saveUpdateInfo(it) }
            setRecheckAlarm()
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }

    private suspend fun deleteSavedUpdateInfo() {
        withContext(Dispatchers.IO) {
            updateInfoDao.apply {
                clearBuildInfo()
                clearChangelogs()
            }
        }
    }

    private suspend fun saveUpdateInfo(updateInfo: UpdateInfo) {
        withContext(Dispatchers.IO) {
            updateInfo.buildInfo?.let {
                updateInfoDao.insertBuildInfo(
                    BuildInfoEntity(
                        version = it.version,
                        date = it.date,
                        url = it.url,
                        fileName = it.fileName,
                        fileSize = it.fileSize,
                        sha512 = it.sha512
                    )
                )
            }
            updateInfo.changelog?.map {
                ChangelogEntity(
                    date = it.key,
                    changelog = it.value
                )
            }?.let {
                updateInfoDao.insertChangelog(it)
            }
        }
    }

    private fun setRecheckAlarm() {
        val alarmIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_CHECK_UPDATE,
            Intent(context, PeriodicUpdateCheckerService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + UPDATE_RECHECK_INTERVAL,
            UPDATE_RECHECK_INTERVAL,
            alarmIntent
        )
    }

    companion object {
        private const val REQUEST_CODE_CHECK_UPDATE = 2001

        // TODO Fetch it from settings when ui for it is setup
        private val UPDATE_RECHECK_INTERVAL = TimeUnit.DAYS.toMillis(7)
    }
}