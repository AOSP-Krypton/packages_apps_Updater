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

package com.flamingo.updater.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

import androidx.core.content.getSystemService

import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.room.BuildInfoEntity
import com.flamingo.updater.data.room.ChangelogEntity
import com.flamingo.updater.data.settings.appSettingsDataStore
import com.flamingo.updater.services.PeriodicUpdateCheckerService

import java.util.concurrent.TimeUnit
import java.util.Date

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainRepository(
    private val context: Context,
    private val updateChecker: UpdateChecker,
    private val applicationScope: CoroutineScope,
    private val fileExportManager: FileExportManager,
    appDatabase: AppDatabase
) {

    private val alarmManager = context.getSystemService<AlarmManager>()!!
    private val alarmIntent = PendingIntent.getService(
        context,
        REQUEST_CODE_CHECK_UPDATE,
        Intent(context, PeriodicUpdateCheckerService::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private val savedStateDatastore = context.savedStateDataStore
    private val appSettingsDataStore = context.appSettingsDataStore
    private val updateInfoDao = appDatabase.updateInfoDao()

    val systemBuildDate = Date(DeviceInfo.getBuildDate())

    val systemBuildVersion: String = DeviceInfo.getBuildVersion()

    val lastCheckedTime: Flow<Date> = savedStateDatastore.data.map { Date(it.lastCheckedTime) }

    val updateFinished: Flow<Boolean> = savedStateDatastore.data.map { it.updateFinished }

    val updateInfo: Flow<UpdateInfo> = updateInfoDao.getBuildInfo().combine(
        updateInfoDao.getChangelogs()
    ) { buildInfoEntity, changelogs ->
        val buildInfo = buildInfoEntity?.let {
            BuildInfo(
                it.version,
                it.date,
                it.preBuildIncremental,
                it.downloadSources,
                it.fileName,
                it.fileSize,
                it.sha512,
            )
        }
        if (buildInfo == null) {
            UpdateInfo.Unavailable
        } else {
            if (UpdateChecker.isNewUpdate(
                    buildInfo,
                    /* this should not be null iff it is incremental ota */
                    buildInfo.preBuildIncremental != null
                )
            ) {
                UpdateInfo.NewUpdate(
                    buildInfo = buildInfo,
                    changelog = changelogs
                )
            } else {
                UpdateInfo.NoUpdate
            }
        }
    }

    /**
     * Check for updates in github.
     *
     * @return a [Result] representing whether fetch was success or not.
     */
    suspend fun fetchUpdateInfo(): Result<Unit> {
        deleteSavedUpdateInfo()
        clearTemporarySavedState()
        val optOutIncremental = appSettingsDataStore.data.map { it.optOutIncremental }.first()
        val updateInfo = withContext(Dispatchers.IO) {
            updateChecker.checkForUpdate(!optOutIncremental)
        }
        savedStateDatastore.updateData {
            it.toBuilder()
                .setLastCheckedTime(System.currentTimeMillis())
                .build()
        }
        return when (updateInfo) {
            is UpdateInfo.NewUpdate -> {
                saveUpdateInfo(updateInfo)
                applicationScope.launch {
                    alarmManager.cancel(alarmIntent)
                    setRecheckAlarm(getUpdateCheckInterval())
                }
                Result.success(Unit)
            }
            is UpdateInfo.Error -> Result.failure(updateInfo.exception)
            else -> Result.success(Unit)
        }
    }

    suspend fun clearTemporarySavedState() {
        savedStateDatastore.updateData {
            it.toBuilder()
                .clearUpdateFinished()
                .clearDownloadFinished()
                .clearLastCheckedTime()
                .build()
        }
    }

    private suspend fun getUpdateCheckInterval(): Int {
        return context.appSettingsDataStore.data.map {
            it.updateCheckInterval
        }.first()
    }

    suspend fun deleteSavedUpdateInfo() {
        withContext(Dispatchers.IO) {
            updateInfoDao.apply {
                clearBuildInfo()
                clearChangelogs()
            }
        }
    }

    private suspend fun saveUpdateInfo(newUpdateInfo: UpdateInfo.NewUpdate) {
        withContext(Dispatchers.IO) {
            newUpdateInfo.buildInfo.let {
                updateInfoDao.insertBuildInfo(
                    BuildInfoEntity(
                        version = it.version,
                        date = it.date,
                        preBuildIncremental = it.preBuildIncremental,
                        downloadSources = it.downloadSources,
                        fileName = it.fileName,
                        fileSize = it.fileSize,
                        sha512 = it.sha512
                    )
                )
            }
            newUpdateInfo.changelog?.map {
                ChangelogEntity(
                    date = it.key,
                    changelog = it.value
                )
            }?.let {
                updateInfoDao.insertChangelog(it)
            }
        }
    }

    /**
     * Schedule alarm for checking updates periodically.
     * Previous alarm will be cancelled so it's safe to
     * call more than once.
     *
     * @param interval the interval as number of days.
     */
    suspend fun setRecheckAlarm(interval: Int) {
        val intervalMillis = TimeUnit.DAYS.toMillis(interval.toLong())
        val lastScheduleTime = savedStateDatastore.data.map { it.lastAlarmScheduleTime }.first()
        val triggerMillis = if (lastScheduleTime > 0) {
            val duration = (System.currentTimeMillis() - lastScheduleTime).milliseconds
            if (duration.isNegative()) {
                intervalMillis
            } else {
                val daysLeft = interval - (duration.inWholeDays % interval)
                if (daysLeft == 0L) {
                    intervalMillis
                } else {
                    daysLeft * 24 * 60 * 60 * 1000
                }
            }
        } else {
            intervalMillis
        }
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + triggerMillis,
            intervalMillis,
            alarmIntent
        )
        savedStateDatastore.updateData {
            it.toBuilder()
                .setLastAlarmScheduleTime(System.currentTimeMillis())
                .build()
        }
    }

    suspend fun getExportDirectoryUri() =
        withContext(Dispatchers.IO) {
            fileExportManager.getExportDirUri()
        }

    companion object {
        private const val REQUEST_CODE_CHECK_UPDATE = 2001
    }
}