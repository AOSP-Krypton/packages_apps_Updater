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
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.krypton.updater.R
import com.krypton.updater.data.DownloadRepository
import com.krypton.updater.ui.MainActivity

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateDownloadService : JobService() {

    private lateinit var serviceScope: CoroutineScope

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var activityIntent: PendingIntent

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private var jobParameters: JobParameters? = null

    private lateinit var cancelIntent: PendingIntent
    private val cancelBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logD("onReceive")
            downloadRepository.cancelDownload()
        }
    }

    // This is to prevent download progress notification from
    // overriding terminal download event notifications because
    // of asynchronous updates.
    private var shouldShowProgressNotification = true

    override fun onCreate() {
        super.onCreate()
        logD("service created")
        serviceScope = CoroutineScope(Dispatchers.Main)
        notificationManager = NotificationManagerCompat.from(this)
        setupNotificationChannel()
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
            Intent(CANCEL_BROADCAST_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )
        registerReceiver(cancelBroadcastReceiver, IntentFilter(CANCEL_BROADCAST_ACTION))
    }

    private fun setupNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID, DOWNLOAD_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        logD("onStartJob")
        this.jobParameters = jobParameters
        showNotification()
        startJob()
        return true
    }

    private fun showNotification() {
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.download_started))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(android.R.string.cancel),
                    cancelIntent
                )
                .build()
        )
    }

    private fun startJob() {
        jobParameters?.let {
            serviceScope.launch {
                launch {
                    listenForEvents()
                }
                shouldShowProgressNotification = true
                launch {
                    listenForProgressUpdates()
                }
                serviceScope.launch {
                    logD("starting download")
                    downloadRepository.startDownload(it.transientExtras)
                }
            }
        }
    }

    private suspend fun listenForEvents() {
        logD("listening for events")
        for (result in downloadRepository.downloadEventChannel) {
            logD("new result $result")
            when {
                result.isFailure -> {
                    shouldShowProgressNotification = false
                    showDownloadFailedNotification(result.exceptionOrNull()?.toString())
                }
                result.isSuccess -> {
                    shouldShowProgressNotification = false
                    showDownloadFinishedNotification()
                }
            }
            notifyJobFinished(result.shouldRetry)
        }
    }

    private fun notifyJobFinished(retry: Boolean) {
        jobParameters?.let {
            jobFinished(it, retry)
        }
    }

    private fun showDownloadFailedNotification(reason: String?) {
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.downloading_failed))
                .setContentText(reason)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun showDownloadFinishedNotification() {
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.downloading_finished))
                .setContentText(downloadRepository.getDownloadFileName())
                .setProgress(100, 100, false)
                .setAutoCancel(true)
                .build()
        )
    }

    private suspend fun listenForProgressUpdates() {
        logD("listening for progress updates")
        downloadRepository.downloadProgressFlow.collect {
            if (shouldShowProgressNotification) updateProgressNotification(it)
        }
    }

    private fun updateProgressNotification(downloadedBytes: Long) {
        val size = downloadRepository.getDownloadSize()
        val progress = if (size > 0) ((downloadedBytes * 100) / size).toInt() else 0
        notificationManager.notify(
            DOWNLOAD_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.downloading_update))
                .setContentText(downloadRepository.getDownloadFileName())
                .setProgress(100, progress, false)
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

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        logD("onStopJob")
        val stopReason = jobParameters.stopReason
        logD("stopReason = $stopReason")
        if (stopReason == JobParameters.STOP_REASON_USER
            || stopReason == JobParameters.STOP_REASON_CANCELLED_BY_APP
        ) {
            notificationManager.cancelAll()
            return false
        }
        return true
    }

    override fun onDestroy() {
        logD("service destroyed")
        unregisterReceiver(cancelBroadcastReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UpdateDownloadService"
        private const val DEBUG = false

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }

        private const val DOWNLOAD_NOTIFICATION_ID = 1002
        private const val DOWNLOAD_NOTIFICATION_CHANNEL_NAME = "Download notification"
        private val DOWNLOAD_NOTIFICATION_CHANNEL_ID = UpdateDownloadService::class.qualifiedName!!

        private const val ACTIVITY_REQUEST_CODE = 10001
        private const val CANCEL_REQUEST_CODE = 20001

        private const val CANCEL_BROADCAST_ACTION = "com.krypton.updater.ACTION_CANCEL_DOWNLOAD"
    }
}
