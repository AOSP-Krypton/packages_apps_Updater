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

package com.flamingo.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import com.flamingo.updater.data.MainRepository

import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.savedStateDataStore
import com.flamingo.updater.data.settings.SettingsRepository
import com.flamingo.updater.data.update.OTAFileManager

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BootCompleteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var otaFileManager: OTAFileManager

    @Inject
    lateinit var mainRepository: MainRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed")
        val pm = context.getSystemService<PowerManager>()!!
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .also { it.acquire(60 * 1000) }
        applicationScope.launch {
            val checkInterval = settingsRepository.updateCheckInterval.first()
            mainRepository.setRecheckAlarm(checkInterval)
            val updateFinished = context.savedStateDataStore.data.map { it.updateFinished }.first()
            if (!updateFinished) return@launch
            Log.i(TAG, "Clearing data")
            mainRepository.clearTemporarySavedState()
            withContext(Dispatchers.IO) {
                appDatabase.updateInfoDao().apply {
                    clearChangelogs()
                    clearBuildInfo()
                }
                context.cacheDir.listFiles()?.forEach {
                    it.delete()
                }
                otaFileManager.wipe()
            }
        }.invokeOnCompletion {
            wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "BootCompleteReceiver"

        private const val WAKELOCK_TAG = "$TAG:WakeLock"
    }
}