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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.updater.R
import com.flamingo.updater.data.update.UpdateRepository
import com.flamingo.updater.data.update.UpdateState
import com.flamingo.updater.ui.MainActivity

import kotlin.math.roundToInt

import kotlinx.coroutines.launch

import org.koin.android.ext.android.inject

class UpdateInstallerService : LifecycleService() {

    private val updateRepository by inject<UpdateRepository>()

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            logD("onReceive, action = $action")
            if (action == ACTION_CANCEL_UPDATE) {
                cancelUpdate()
            } else if (action == ACTION_REBOOT) {
                reboot()
            }
        }
    }

    private lateinit var oldConfig: Configuration
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var activityIntent: PendingIntent
    private lateinit var cancelIntent: PendingIntent
    private lateinit var rebootIntent: PendingIntent
    private lateinit var updateNotificationBuilder: NotificationCompat.Builder

    private var binder: IBinder? = null
    private var currentProgress = 0

    override fun onCreate() {
        super.onCreate()
        logD("onCreate")
        oldConfig = resources.configuration
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        setupIntents()
        binder = ServiceBinder()
        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(ACTION_CANCEL_UPDATE)
            addAction(ACTION_REBOOT)
        })
        listenForEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_UPDATE) {
            startUpdate()
        }
        return super.onStartCommand(intent, flags, startId)
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
                UPDATE_INSTALLATION_CHANNEL_ID,
                getString(R.string.update_install_service_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun setupIntents() {
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        cancelIntent = PendingIntent.getBroadcast(
            this,
            CANCEL_REQUEST_CODE,
            Intent(ACTION_CANCEL_UPDATE),
            PendingIntent.FLAG_IMMUTABLE
        )
        rebootIntent = PendingIntent.getBroadcast(
            this,
            REBOOT_REQUEST_CODE,
            Intent(ACTION_REBOOT),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun listenForEvents() {
        lifecycleScope.launch {
            updateRepository.updateState.collect {
                when (it) {
                    is UpdateState.Idle, UpdateState.Initializing -> currentProgress = 0
                    is UpdateState.Verifying -> updateProgressNotification(it.progress)
                    is UpdateState.Updating -> updateProgressNotification(it.progress)
                    is UpdateState.Paused -> showUpdatePausedNotification()
                    is UpdateState.Failed -> {
                        showUpdateFailedNotification(it.exception.localizedMessage)
                        currentProgress = 0
                    }
                    is UpdateState.Finished -> {
                        showUpdateFinishedNotification()
                        currentProgress = 0
                    }
                }
            }
        }
    }

    private fun showUpdatePausedNotification() {
        notificationManager.notify(
            UPDATE_INSTALLATION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.installation_paused))
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(android.R.string.cancel),
                    cancelIntent
                )
                .build()
        )
    }

    private fun showUpdateFailedNotification(reason: String?) {
        notificationManager.notify(
            UPDATE_INSTALLATION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.installation_failed))
                .setContentText(reason)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun showUpdateFinishedNotification() {
        notificationManager.notify(
            UPDATE_INSTALLATION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.installation_finished))
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_baseline_restart_24,
                    getString(R.string.reboot),
                    rebootIntent
                )
                .build()
        )
    }

    private fun updateProgressNotification(progress: Float) {
        if (!::updateNotificationBuilder.isInitialized) {
            updateNotificationBuilder =
                NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                    .setContentIntent(activityIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setOngoing(true)
                    .setSilent(true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(android.R.string.cancel),
                        cancelIntent
                    )
            notificationManager.notify(
                UPDATE_INSTALLATION_NOTIFICATION_ID,
                updateNotificationBuilder
                    .setContentTitle(
                        getString(
                            R.string.installing_update_format,
                            String.format("%.2f", progress)
                        )
                    )
                    .setProgress(100, currentProgress, false)
                    .build()
            )
        }
        val newProgress = progress.roundToInt()
        if (newProgress != currentProgress) {
            currentProgress = newProgress
            notificationManager.notify(
                UPDATE_INSTALLATION_NOTIFICATION_ID,
                updateNotificationBuilder
                    .setContentTitle(
                        getString(
                            R.string.installing_update_format,
                            String.format("%.2f", progress)
                        )
                    )
                    .setProgress(100, currentProgress, false)
                    .build()
            )
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        logD("onBind")
        return binder
    }

    override fun onDestroy() {
        logD("onDestroy")
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun startUpdate() {
        logD("starting update")
        lifecycleScope.launch {
            updateRepository.start()
        }
    }

    fun pauseOrResumeUpdate() {
        if (!updateRepository.supportsUpdateSuspension) {
            logD("Does not support suspending update, aborting")
            return
        }
        logD("pauseOrResumeUpdate, paused = ${updateRepository.isUpdatePaused}")
        if (updateRepository.isUpdatePaused) {
            lifecycleScope.launch {
                updateRepository.resume()
            }
        } else {
            lifecycleScope.launch {
                updateRepository.pause()
            }
        }
    }

    fun cancelUpdate() {
        logD("cancelUpdate, updating = ${updateRepository.isUpdating}")
        if (updateRepository.isUpdating) {
            lifecycleScope.launch {
                updateRepository.cancel()
            }
            stop()
        }
    }

    fun reboot() {
        logD("rebooting")
        lifecycleScope.launch {
            updateRepository.reboot()
        }
    }

    private fun stop() {
        logD("stop")
        stopSelf()
        notificationManager.cancel(UPDATE_INSTALLATION_NOTIFICATION_ID)
    }

    inner class ServiceBinder : Binder() {
        val service: UpdateInstallerService
            get() = this@UpdateInstallerService
    }

    companion object {
        private const val TAG = "UpdateInstallerService"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private const val UPDATE_INSTALLATION_NOTIFICATION_ID = 4
        private val UPDATE_INSTALLATION_CHANNEL_ID = "${UpdateInstallerService::class.qualifiedName!!}_NotificationChannel"

        private const val ACTIVITY_REQUEST_CODE = 1
        private const val CANCEL_REQUEST_CODE = 2
        private const val REBOOT_REQUEST_CODE = 3

        const val ACTION_START_UPDATE = "com.flamingo.updater.ACTION_START_UPDATE"
        private const val ACTION_CANCEL_UPDATE = "com.flamingo.updater.ACTION_CANCEL_UPDATE"
        private const val ACTION_REBOOT = "com.flamingo.updater.ACTION_REBOOT"

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}