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
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.flamingo.updater.R
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.download.DownloadState
import com.flamingo.updater.ui.MainActivity

import kotlin.math.roundToInt

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.koin.android.ext.android.inject

class UpdateDownloadService : JobService() {

    private val downloadRepository by inject<DownloadRepository>()

    private val cancelBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logD("onReceive")
            downloadRepository.cancelDownload()
        }
    }

    private lateinit var oldConfig: Configuration
    private lateinit var serviceScope: CoroutineScope
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var activityIntent: PendingIntent
    private lateinit var cancelIntent: PendingIntent
    private lateinit var downloadNotificationBuilder: NotificationCompat.Builder

    private var jobParameters: JobParameters? = null
    private var currentProgress = 0

    override fun onCreate() {
        super.onCreate()
        logD("service created")
        oldConfig = resources.configuration
        serviceScope = CoroutineScope(Dispatchers.Main)
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        createIntents()
        registerReceiver(cancelBroadcastReceiver, IntentFilter(CANCEL_BROADCAST_ACTION))
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID, DOWNLOAD_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun createIntents() {
        activityIntent = PendingIntent.getActivity(
            this,
            ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        cancelIntent = PendingIntent.getBroadcast(
            this,
            CANCEL_REQUEST_CODE,
            Intent(CANCEL_BROADCAST_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartJob(jobParameters: JobParameters): Boolean {
        logD("onStartJob")
        this.jobParameters = jobParameters
        showNotification()
        startJob()
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.diff(oldConfig) == ActivityInfo.CONFIG_LOCALE) {
            createNotificationChannel()
        }
        oldConfig = newConfig
    }

    private fun showNotification() {
        notificationManager.notify(
            DOWNLOAD_PROGRESS_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                .setContentTitle(getString(R.string.downloading))
                .setProgress(100, 0, true)
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
                listenForEvents()
            }
            serviceScope.launch {
                logD("starting download")
                downloadRepository.startDownload(it.transientExtras)
            }
        }
    }

    private suspend fun listenForEvents() {
        logD("listening for events")
        downloadRepository.downloadState.collect {
            logD("New state $it")
            when (it) {
                is DownloadState.Idle,
                is DownloadState.Waiting -> {
                    currentProgress = 0
                }
                is DownloadState.Downloading -> updateProgressNotification(it.progress)
                is DownloadState.Failed -> {
                    showDownloadFailedNotification(it.exception?.localizedMessage)
                    currentProgress = 0
                    notifyJobFinished(false)
                }
                is DownloadState.Finished -> {
                    showDownloadFinishedNotification()
                    currentProgress = 0
                    notifyJobFinished(false)
                }
                is DownloadState.Retry -> notifyJobFinished(true)
            }
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
                .setContentText(downloadRepository.downloadFileName)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun updateProgressNotification(progress: Float) {
        if (!::downloadNotificationBuilder.isInitialized) {
            downloadNotificationBuilder =
                NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .setContentIntent(activityIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setContentTitle(getString(R.string.downloading_update))
                    .setContentText(downloadRepository.downloadFileName)
                    .setOngoing(true)
                    .setSilent(true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(android.R.string.cancel),
                        cancelIntent
                    )
            notificationManager.notify(
                DOWNLOAD_PROGRESS_NOTIFICATION_ID,
                downloadNotificationBuilder.setProgress(100, currentProgress, false).build()
            )
        }
        val newProgress = progress.roundToInt()
        if (newProgress != currentProgress) {
            currentProgress = newProgress
            notificationManager.notify(
                DOWNLOAD_PROGRESS_NOTIFICATION_ID,
                downloadNotificationBuilder.setProgress(100, currentProgress, false).build()
            )
        }
    }

    override fun onStopJob(jobParameters: JobParameters): Boolean {
        logD("onStopJob, stopReason = ${jobParameters.stopReason}")
        return true
    }

    override fun onDestroy() {
        logD("service destroyed")
        unregisterReceiver(cancelBroadcastReceiver)
        serviceScope.cancel()
        notificationManager.cancel(DOWNLOAD_PROGRESS_NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UpdateDownloadService"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }

        private const val DOWNLOAD_NOTIFICATION_ID = 2
        private const val DOWNLOAD_PROGRESS_NOTIFICATION_ID = 3
        private const val DOWNLOAD_NOTIFICATION_CHANNEL_NAME = "Download notification"
        private val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "${UpdateDownloadService::class.qualifiedName!!}_NotificationChannel"

        private const val ACTIVITY_REQUEST_CODE = 1
        private const val CANCEL_REQUEST_CODE = 2

        private const val CANCEL_BROADCAST_ACTION = "com.flamingo.updater.ACTION_CANCEL_DOWNLOAD"
    }
}
