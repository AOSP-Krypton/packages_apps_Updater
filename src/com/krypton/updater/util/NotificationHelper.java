/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.util;

import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.widget.Toast.LENGTH_SHORT;
import static androidx.core.app.NotificationCompat.PRIORITY_DEFAULT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;

import com.krypton.updater.R;
import com.krypton.updater.ui.activity.UpdaterActivity;
import com.krypton.updater.UpdaterApplication;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NotificationHelper {
    private static final int ACTIVITY_REQUEST_CODE = 1000;
    private static final int UPDATER_NOTIF_ID = 1001;
    private final String TAG;
    private final Context context;
    private final NotificationManager manager;
    private final PendingIntent activityIntent;

    @Inject
    public NotificationHelper(Context context) {
        this.context = context;
        TAG = context.getString(R.string.app_name);
        manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(TAG, TAG, IMPORTANCE_HIGH));
        final Intent intent = new Intent(context, UpdaterActivity.class);
        intent.setFlags(FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent = PendingIntent.getActivity(context,
            ACTIVITY_REQUEST_CODE, intent, 0);
    }

    public void notify(int id, Notification notif) {
        NotificationManagerCompat.from(context).notify(id, notif);
    }

    public synchronized void showCancellableNotification(int titleId, int descId) {
        notify(UPDATER_NOTIF_ID, getDefaultBuilder()
                .setSmallIcon(android.R.drawable.ic_info)
                .setContentTitle(context.getString(titleId))
                .setContentText(context.getString(descId))
                .setAutoCancel(true)
                .build());
    }

    public void removeNotificationForId(int id) {
        manager.cancel(id);
    }

    public void removeAllNotifications() {
        manager.cancelAll();
    }

    public void onlyNotify(int titleId, int msgId) {
        if (!UpdaterApplication.isUIVisible()) {
            showCancellableNotification(titleId, msgId);
        }
    }

    public void notifyOrToast(int titleId, int msgId, Handler handler) {
        if (UpdaterApplication.isUIVisible()) {
            handler.post(() -> Toast.makeText(context, msgId, LENGTH_SHORT).show());
        } else {
            showCancellableNotification(titleId, msgId);
        }
    }

    public Builder getDefaultBuilder() {
        return new Builder(context, TAG)
            .setContentIntent(activityIntent)
            .setPriority(PRIORITY_DEFAULT);
    }
}
