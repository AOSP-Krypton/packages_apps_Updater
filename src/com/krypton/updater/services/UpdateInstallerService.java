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

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.krypton.updater.model.repos.UpdateRepository;
import com.krypton.updater.R;
import com.krypton.updater.util.*;
import com.krypton.updater.UpdaterApplication;

import javax.inject.Inject;

public class UpdateInstallerService extends Service {

    private static final String TAG = "UpdateInstallerService";
    private static final String WL_TAG = "UpdateInstallerService.WakeLock";
    private static final int UPDATE_INSTALLATION_NOTIF_ID = 1002;
    private IBinder binder;
    private PowerManager powerManager;
    private WakeLock wakeLock;
    private UpdateRepository repository;
    private NotificationHelper notificationHelper;
    private boolean updateStarted, updatePaused;

    @Inject
    void setDependencies(UpdateRepository repository, NotificationHelper notificationHelper) {
        this.repository = repository;
        this.notificationHelper = notificationHelper;
    }

    @Override
    public void onCreate() {
        Utils.log("onCreate, updateStarted", updateStarted);
        ((UpdaterApplication) getApplication()).getComponent().inject(this);
        binder = new ServiceBinder();
        powerManager = getSystemService(PowerManager.class);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, WL_TAG);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.log("onStartCommand, updateStarted", updateStarted);
        if (intent != null && intent.getAction().equals(ACION_START_UPDATE)) {
            startUpdate();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Utils.log("onDestroy");
        stopForeground(true);
        releaseWakeLock();
    }

    private void startUpdate() {
        Utils.log("startUpdate");
        updateStarted = true;
        Utils.log("updateStarted", updateStarted);
        startForeground();
        acquireWakeLock();
        repository.startUpdate();
    }

    public void pauseUpdate() {
        Utils.log("pauseUpdate");
        if (updateStarted) {
            updatePaused = !updatePaused;
            if (updatePaused) {
                Utils.log("pausing");
                releaseWakeLock();
                stopForeground(true);
            } else {
                Utils.log("resuming");
                acquireWakeLock();
                startForeground();
            }
            repository.pauseUpdate(updatePaused);
        }
    }

    public void cancelUpdate() {
        Utils.log("cancelUpdate");
        Utils.log("updateStarted", updateStarted);
        if (updateStarted) {
            Utils.log("updateStarted, cancelling");
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
            notificationHelper.getDefaultBuilder()
                .setContentTitle(getString(R.string.update_in_progress))
                .setOngoing(true)
                .build());
    }

    public final class ServiceBinder extends Binder {
        public UpdateInstallerService getService() {
            return UpdateInstallerService.this;
        }
    }
}
