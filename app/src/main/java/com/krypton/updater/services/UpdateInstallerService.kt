/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.UpdateLock
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.krypton.updater.R
import com.krypton.updater.data.update.UpdateRepository
import com.krypton.updater.ui.MainActivity

import dagger.hilt.android.AndroidEntryPoint

import java.util.concurrent.TimeUnit

import javax.inject.Inject

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateInstallerService : Service() {
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var updateLock: UpdateLock
    private lateinit var serviceScope: CoroutineScope

    @Inject
    lateinit var updateRepository: UpdateRepository

    private var binder: IBinder? = null

    private lateinit var activityIntent: PendingIntent
    private lateinit var cancelIntent: PendingIntent
    private lateinit var rebootIntent: PendingIntent

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

    private var shouldShowProgressNotification = true

    override fun onCreate() {
        logD("onCreate")
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.Main)
        setupNotificationChannel()
        setupIntents()
        powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        updateLock = UpdateLock(UPDATE_LOCK_TAG)
        binder = ServiceBinder()
        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(ACTION_CANCEL_UPDATE)
            addAction(ACTION_REBOOT)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_UPDATE) {
            startUpdate()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun setupNotificationChannel() {
        notificationManager = NotificationManagerCompat.from(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                UPDATE_INSTALLATION_CHANNEL_ID, UPDATE_INSTALLATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun setupIntents() {
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
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
        serviceScope.launch {
            updateRepository.updateState.collect {
                shouldShowProgressNotification = it.initializing || it.updating
                when {
                    it.initializing -> updateProgressNotification(0f, true)
                    it.updating -> updateProgressNotification(0f)
                    it.paused -> {
                        releaseAndStopFg()
                        showUpdatePausedNotification()
                    }
                    it.failed -> {
                        releaseAndStopFg()
                        showUpdateFailedNotification(it.exception?.message)
                    }
                    it.finished -> {
                        releaseAndStopFg()
                        showUpdateFinishedNotification()
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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.installation_finished))
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_baseline_restart_24,
                    getString(R.string.reboot),
                    rebootIntent
                )
                .build()
        )
    }

    private fun listenForProgressUpdates() {
        serviceScope.launch {
            updateRepository.updateProgress.collect {
                updateProgressNotification(it)
            }
        }
    }

    private fun updateProgressNotification(progress: Float, indeterminate: Boolean = false) {
        if (!shouldShowProgressNotification) return
        notificationManager.notify(
            UPDATE_INSTALLATION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(
                    getString(
                        R.string.installing_update_format,
                        String.format("%.2f", progress)
                    )
                )
                .setProgress(100, progress.toInt(), indeterminate)
                .setOngoing(true)
                .setSilent(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(android.R.string.cancel),
                    cancelIntent
                )
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        logD("onBind")
        return binder
    }

    override fun onDestroy() {
        logD("onDestroy")
        unregisterReceiver(broadcastReceiver)
        serviceScope.cancel()
        releaseLocks()
    }

    private fun startUpdate() {
        logD("starting update")
        listenForEvents()
        listenForProgressUpdates()
        acquireAndStartFg()
        serviceScope.launch {
            updateRepository.start()
        }
    }

    fun pauseOrResumeUpdate() {
        logD("pauseOrResumeUpdate, paused = ${updateRepository.isUpdatePaused}")
        if (updateRepository.isUpdatePaused) {
            acquireAndStartFg()
            serviceScope.launch {
                updateRepository.resume()
            }
        } else {
            serviceScope.launch {
                updateRepository.pause()
            }
            releaseAndStopFg()
        }
    }

    fun cancelUpdate() {
        logD("cancelUpdate, updating = ${updateRepository.isUpdating}")
        if (updateRepository.isUpdating) {
            serviceScope.launch {
                updateRepository.cancel()
            }
            stop()
        }
    }

    fun reboot() {
        logD("rebooting")
        powerManager.reboot(null)
    }

    private fun releaseLocks() {
        logD("releaseLocks")
        if (wakeLock.isHeld) {
            logD("Releasing wake lock")
            wakeLock.release()
        }
        if (updateLock.isHeld) {
            logD("Releasing update lock")
            updateLock.release()
        }
    }

    private fun acquireLocks() {
        logD("acquireLocks")
        if (!wakeLock.isHeld) {
            logD("Acquiring wake lock")
            wakeLock.acquire(WAKELOCK_TIMEOUT)
        }
        if (!updateLock.isHeld) {
            logD("Acquiring update lock")
            updateLock.acquire()
        }
    }

    private fun releaseAndStopFg() {
        releaseLocks()
        stopForeground(true)
    }

    private fun acquireAndStartFg() {
        acquireLocks()
        startForeground()
    }

    private fun startForeground() {
        logD("startForeground")
        val notification = NotificationCompat.Builder(this, UPDATE_INSTALLATION_CHANNEL_ID)
            .setContentIntent(activityIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_baseline_system_update_24)
            .setContentTitle(getString(R.string.installing_update))
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(android.R.string.cancel),
                cancelIntent
            )
            .build()
        startForeground(UPDATE_INSTALLATION_NOTIFICATION_ID, notification)
    }

    private fun stop() {
        logD("stop")
        releaseAndStopFg()
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

        private const val WAKELOCK_TAG = "$TAG:WakeLock"
        private const val UPDATE_LOCK_TAG = "$TAG:UpdateLock"
        private val WAKELOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(30)

        private const val UPDATE_INSTALLATION_NOTIFICATION_ID = 2002
        private val UPDATE_INSTALLATION_CHANNEL_ID = UpdateDownloadService::class.qualifiedName!!
        private const val UPDATE_INSTALLATION_CHANNEL_NAME = "Update install"

        private const val ACTIVITY_REQUEST_CODE = 3001
        private const val CANCEL_REQUEST_CODE = 30001
        private const val REBOOT_REQUEST_CODE = 40001

        const val ACTION_START_UPDATE = "com.krypton.updater.ACTION_START_UPDATE"
        private const val ACTION_CANCEL_UPDATE = "com.krypton.updater.ACTION_CANCEL_UPDATE"
        private const val ACTION_REBOOT = "com.krypton.updater.ACTION_REBOOT"

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}