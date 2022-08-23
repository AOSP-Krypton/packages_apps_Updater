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
import android.os.SystemClock
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.flamingo.updater.R
import com.flamingo.updater.data.settings.SettingsRepository
import com.flamingo.updater.data.update.UpdateRepository
import com.flamingo.updater.data.update.UpdateState
import com.flamingo.updater.ui.MainActivity

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch

import org.koin.android.ext.android.inject

class UpdateInstallerService : LifecycleService() {

    private val updateRepository by inject<UpdateRepository>()
    private val settingsRepository by inject<SettingsRepository>()

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                ACTION_CANCEL_UPDATE -> cancelUpdate()
                ACTION_REBOOT -> reboot()
                ACTION_CANCEL_AUTO_REBOOT -> cancelAutoReboot()
            }
        }
    }

    private lateinit var oldConfig: Configuration
    private lateinit var binder: IBinder
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var activityIntent: PendingIntent
    private lateinit var cancelIntent: PendingIntent
    private lateinit var rebootIntent: PendingIntent
    private lateinit var cancelAutoRebootIntent: PendingIntent
    private lateinit var updateNotificationBuilder: NotificationCompat.Builder

    private var currentProgress = 0
    private var autoRebootTimer: Job? = null

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
            addAction(ACTION_CANCEL_AUTO_REBOOT)
        })
        listenForEvents()
        observeAutoRebootSettings()
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        cancelIntent = PendingIntent.getBroadcast(
            this,
            CANCEL_REQUEST_CODE,
            Intent(ACTION_CANCEL_UPDATE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rebootIntent = PendingIntent.getBroadcast(
            this,
            REBOOT_REQUEST_CODE,
            Intent(ACTION_REBOOT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        cancelAutoRebootIntent = PendingIntent.getBroadcast(
            this,
            CANCEL_AUTO_REBOOT_REQUEST_CODE,
            Intent(ACTION_CANCEL_AUTO_REBOOT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
                        currentProgress = 0
                        lifecycleScope.launch {
                            val autoReboot = settingsRepository.autoReboot.first()
                            if (autoReboot) {
                                startAutoRebootTimer()
                            } else {
                                showUpdateFinishedNotification()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeAutoRebootSettings() {
        lifecycleScope.launch {
            settingsRepository.autoReboot.collect {
                if (!it && autoRebootTimer?.isActive == true) {
                    cancelAutoReboot()
                }
            }
        }
    }

    private fun cancelAutoReboot() {
        if (autoRebootTimer?.isActive != true) return
        autoRebootTimer?.cancel()
        autoRebootTimer = null
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

    private fun startAutoRebootTimer() {
        if (autoRebootTimer?.isActive == true) return
        autoRebootTimer = lifecycleScope.launch {
            var durationLeft = settingsRepository.autoRebootDelay.first().milliseconds
            var currentTime = SystemClock.uptimeMillis()
            do {
                val duration = SystemClock.uptimeMillis() - currentTime
                durationLeft -= duration.milliseconds
                updateAutoRebootNotification(durationLeft)
                currentTime = SystemClock.uptimeMillis()
            } while (durationLeft.isPositive())
        }
    }

    private fun updateAutoRebootNotification(duration: Duration) {
        notificationManager.notify(
            UPDATE_INSTALLATION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.installation_finished))
                .setContentText(getString(R.string.auto_reboot_notification_text, durationToString(duration)))
                .addAction(
                    R.drawable.baseline_cancel_24,
                    getString(android.R.string.cancel),
                    cancelAutoRebootIntent
                )
                .addAction(
                    R.drawable.ic_baseline_restart_24,
                    getString(R.string.reboot),
                    rebootIntent
                )
                .build()
        )
    }

    private fun durationToString(duration: Duration): String {
        return duration.toComponents { minutes, seconds, _ ->
            "$minutes:${if (seconds < 10) "0$seconds" else seconds}"
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        logD("onBind")
        return binder
    }

    override fun onDestroy() {
        logD("onDestroy")
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    fun startUpdate() {
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
        private const val CANCEL_AUTO_REBOOT_REQUEST_CODE = 4

        private const val ACTION_CANCEL_UPDATE = "com.flamingo.updater.action.CANCEL_UPDATE"
        private const val ACTION_REBOOT = "com.flamingo.updater.action.REBOOT"
        private const val ACTION_CANCEL_AUTO_REBOOT = "com.flamingo.updater.action.CANCEL_AUTO_REBOOT"

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}