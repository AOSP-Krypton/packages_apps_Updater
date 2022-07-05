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

package com.flamingo.updater.services

import android.annotation.StringRes
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.flamingo.updater.R
import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.UpdateInfo
import com.flamingo.updater.ui.MainActivity

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PeriodicUpdateCheckerService : Service() {

    private lateinit var activityIntent: PendingIntent

    @Inject
    lateinit var mainRepository: MainRepository

    private lateinit var serviceScope: CoroutineScope
    private lateinit var notificationManager: NotificationManagerCompat

    private var updateCheckerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.Main)
        notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                UPDATE_NOTIFICATION_CHANNEL_ID, UPDATE_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        updateCheckerJob = serviceScope.launch {
            val result = mainRepository.fetchUpdateInfo()
            if (result.isFailure)
                notifyUser(
                    R.string.auto_update_check_failed,
                    R.string.auto_update_check_failed_desc
                )
            val updateInfo = mainRepository.getUpdateInfo().first()
            if (updateInfo.type == UpdateInfo.Type.NEW_UPDATE) {
                notifyUser(R.string.new_system_update, R.string.new_system_update_description)
            }
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        updateCheckerJob?.cancel()
        updateCheckerJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notifyUser(@StringRes titleId: Int, @StringRes descId: Int) {
        notificationManager.notify(
            UPDATE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(com.android.internal.R.drawable.ic_info)
                .setContentTitle(getString(titleId))
                .setContentText(getString(descId))
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        private const val UPDATE_NOTIFICATION_ID = 1001
        private const val UPDATE_NOTIFICATION_CHANNEL_NAME = "Update notification"
        private val UPDATE_NOTIFICATION_CHANNEL_ID = PeriodicUpdateCheckerService::class.qualifiedName!!

        private const val ACTIVITY_REQUEST_CODE = 10001
    }
}