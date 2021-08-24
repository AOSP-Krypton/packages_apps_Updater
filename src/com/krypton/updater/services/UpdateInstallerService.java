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

package com.krypton.updater.services;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static com.krypton.updater.util.Constants.ACION_START_UPDATE;
import static com.krypton.updater.util.Constants.CANCELLED;
import static com.krypton.updater.util.Constants.FAILED;
import static com.krypton.updater.util.Constants.FINISHED;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.core.app.NotificationCompat.Builder;

import com.krypton.updater.model.repos.UpdateRepository;
import com.krypton.updater.R;
import com.krypton.updater.util.NotificationHelper;
import com.krypton.updater.UpdaterApplication;

import io.reactivex.rxjava3.disposables.Disposable;

import javax.inject.Inject;

public class UpdateInstallerService extends Service {
    private static final String WL_TAG = "UpdateInstallerService.WakeLock";
    private static final int UPDATE_INSTALLATION_NOTIF_ID = 1002;
    private IBinder binder;
    private PowerManager powerManager;
    private WakeLock wakeLock;
    private UpdateRepository repository;
    private NotificationHelper notificationHelper;
    private Builder notificationBuilder;
    private Disposable disposable;
    private boolean updateStarted, updatePaused;

    @Inject
    void setDependencies(UpdateRepository repository, NotificationHelper notificationHelper) {
        this.repository = repository;
        this.notificationHelper = notificationHelper;
        notificationBuilder = notificationHelper.getDefaultBuilder()
            .setNotificationSilent()
            .setOngoing(true);
    }

    @Override
    public void onCreate() {
        ((UpdaterApplication) getApplication()).getComponent().inject(this);
        binder = new ServiceBinder();
        powerManager = getSystemService(PowerManager.class);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, WL_TAG);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction().equals(ACION_START_UPDATE)) {
            startUpdate();
            disposable = repository.getUpdateStatusProcessor()
                .filter(status -> status.getStatusCode() != 0)
                .subscribe(status -> {
                    final int code = status.getStatusCode();
                    if (code == FINISHED || code == CANCELLED || code == FAILED) {
                        stopSelf();
                    } else {
                        final int progress = status.getProgress();
                        notificationHelper.notify(UPDATE_INSTALLATION_NOTIF_ID,
                            notificationBuilder.setContentTitle(
                                    getUpdateStepString(status.getStep()))
                                .setContentText(String.valueOf(progress) + "%")
                                .setProgress(100, progress, false)
                                .build());
                    }
                });
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        stopForeground(true);
        releaseWakeLock();
    }

    private void startUpdate() {
        updateStarted = true;
        startForeground();
        acquireWakeLock();
        repository.startUpdate();
    }

    public void pauseUpdate() {
        if (updateStarted) {
            updatePaused = !updatePaused;
            if (updatePaused) {
                releaseWakeLock();
                stopForeground(true);
            } else {
                acquireWakeLock();
                startForeground();
            }
            repository.pauseUpdate(updatePaused);
        }
    }

    public void cancelUpdate() {
        if (updateStarted) {
            stopForeground(true);
            releaseWakeLock();
            repository.cancelUpdate();
            updatePaused = false;
            updateStarted = false;
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void startForeground() {
        startForeground(UPDATE_INSTALLATION_NOTIF_ID,
            notificationBuilder.setContentTitle(getString(
                R.string.update_in_progress)).build());
    }

    private String getUpdateStepString(int step) {
        if (step == 1) {
            return getString(R.string.processing_payload);
        } else if (step == 2) {
            return getString(R.string.applying_update);
        }
        return null;
    }

    public final class ServiceBinder extends Binder {
        public UpdateInstallerService getService() {
            return UpdateInstallerService.this;
        }
    }
}
