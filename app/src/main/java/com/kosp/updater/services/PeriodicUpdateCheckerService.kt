/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
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

package com.kosp.updater.services

import android.annotation.StringRes
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.kosp.updater.R
import com.kosp.updater.data.MainRepository
import com.kosp.updater.data.UpdateInfo
import com.kosp.updater.ui.MainActivity

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import org.koin.android.ext.android.inject

class PeriodicUpdateCheckerService : LifecycleService() {

    private val mainRepository by inject<MainRepository>()

    private lateinit var oldConfig: Configuration
    private lateinit var activityIntent: PendingIntent
    private lateinit var notificationManager: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        oldConfig = resources.configuration
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        lifecycleScope.launch {
            mainRepository.fetchUpdateInfo().onFailure {
                notifyUser(
                    R.string.auto_update_check_failed,
                    R.string.auto_update_check_failed_desc
                )
                stopSelf()
                return@launch
            }
            val updateInfo = mainRepository.updateInfo.first()
            if (updateInfo is UpdateInfo.NewUpdate) {
                notifyUser(R.string.new_system_update, R.string.new_system_update_description)
            }
            stopSelf()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.diff(oldConfig) == ActivityInfo.CONFIG_LOCALE) {
            createNotificationChannel()
        }
        oldConfig = newConfig
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                UPDATE_NOTIFICATION_CHANNEL_ID,
                getString(R.string.update_checker_service_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
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
        private const val UPDATE_NOTIFICATION_ID = 1
        private val UPDATE_NOTIFICATION_CHANNEL_ID =
            "${PeriodicUpdateCheckerService::class.qualifiedName!!}_NotificationChannel"

        private const val ACTIVITY_REQUEST_CODE = 1
    }
}