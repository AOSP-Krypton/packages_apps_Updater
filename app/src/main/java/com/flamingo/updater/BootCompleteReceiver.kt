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
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.settings.SettingsRepository
import com.flamingo.updater.data.update.UpdateRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootCompleteReceiver : BroadcastReceiver(), KoinComponent {

    private val applicationScope by inject<CoroutineScope>()
    private val mainRepository by inject<MainRepository>()
    private val downloadRepository by inject<DownloadRepository>()
    private val settingsRepository by inject<SettingsRepository>()
    private val updateRepository by inject<UpdateRepository>()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed")
        val pm = context.getSystemService<PowerManager>()!!
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .also { it.acquire(60 * 1000) }
        applicationScope.launch {
            val checkInterval = settingsRepository.updateCheckInterval.first()
            mainRepository.setRecheckAlarm(checkInterval)
            val updateFinished = mainRepository.updateFinished.first()
            if (!updateFinished) return@launch
            Log.i(TAG, "Clearing data")
            mainRepository.clearTemporarySavedState()
            mainRepository.deleteSavedUpdateInfo()
            downloadRepository.clearCache()
            updateRepository.wipeWorkspaceDirectory()
        }.invokeOnCompletion {
            wakeLock.release()
        }
    }

    companion object {
        private const val TAG = "BootCompleteReceiver"

        private const val WAKELOCK_TAG = "$TAG:WakeLock"
    }
}