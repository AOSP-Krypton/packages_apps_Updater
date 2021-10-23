/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.krypton.updater.R
import com.krypton.updater.ui.activity.UpdaterActivity
import com.krypton.updater.UpdaterApplication

import javax.inject.Inject
import javax.inject.Singleton

private const val ACTIVITY_REQUEST_CODE: Int = 1000
private const val UPDATER_NOTIFICATION_ID: Int = 1001

@Singleton
class NotificationHelper @Inject constructor(ctx: Context) {
    private val context: Context
    private val appName: String
    private val notificationManager: NotificationManager
    private val activityIntent: PendingIntent

    init {
        context = ctx
        appName = context.getString(R.string.app_name)

        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(NotificationChannel(
            appName, appName,
            NotificationManager.IMPORTANCE_HIGH))

        val intent = Intent(context, UpdaterActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activityIntent = PendingIntent.getActivity(context,
            ACTIVITY_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE)
    }

    @Synchronized
    fun notify(
        notificationId: Int,
        notification: Notification,
    ) {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    @Synchronized
    fun showCancellableNotification(
        titleId: Int,
        descId: Int,
    ) {
        notify(UPDATER_NOTIFICATION_ID, getDefaultBuilder()
            .setSmallIcon(android.R.drawable.ic_info)
            .setContentTitle(context.getString(titleId))
            .setContentText(context.getString(descId))
            .setAutoCancel(true)
            .build())
    }

    @Synchronized
    fun removeNotificationForId(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    @Synchronized
    fun removeAllNotifications() {
        notificationManager.cancelAll()
    }

    @Synchronized
    fun onlyNotify(
        titleId: Int,
        msgId: Int,
    ) {
        if (!UpdaterApplication.isUIVisible()) {
            showCancellableNotification(titleId, msgId)
        }
    }

    @Synchronized
    fun notifyOrToast(
        titleId: Int,
        msgId: Int,
        handler: Handler,
    ) {
        if (UpdaterApplication.isUIVisible()) {
            handler.post{ Toast.makeText(context, msgId,
                Toast.LENGTH_SHORT).show() }
        } else {
            showCancellableNotification(titleId, msgId)
        }
    }

    @Synchronized
    fun getDefaultBuilder() = NotificationCompat.Builder(context, appName)
        .setContentIntent(activityIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
}
