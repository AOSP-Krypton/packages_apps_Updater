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
import android.util.Log

import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.savedStateDataStore
import com.flamingo.updater.data.update.OTAFileManager

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootCompleteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var otaFileManager: OTAFileManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed")
        applicationScope.launch(Dispatchers.IO) {
            val updateFinished = context.savedStateDataStore.data.map { it.updateFinished }.first()
            if (!updateFinished) return@launch
            Log.i(TAG, "Clearing data")
            context.savedStateDataStore.updateData {
                it.toBuilder()
                    .clear()
                    .build()
            }
            appDatabase.updateInfoDao().apply {
                clearChangelogs()
                clearBuildInfo()
            }
            context.cacheDir.listFiles()?.forEach {
                it.delete()
            }
            otaFileManager.wipe()
        }
    }

    companion object {
        private const val TAG = "BootCompleteReceiver"
    }
}