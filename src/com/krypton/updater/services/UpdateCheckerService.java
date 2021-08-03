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

import static com.krypton.updater.util.Constants.NEW_UPDATE;
import static com.krypton.updater.util.Constants.REFRESHING;
import static com.krypton.updater.util.Constants.REFRESH_FAILED;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.krypton.updater.model.repos.AppRepository;
import com.krypton.updater.model.data.Response;
import com.krypton.updater.R;
import com.krypton.updater.util.NotificationHelper;
import com.krypton.updater.UpdaterApplication;

import io.reactivex.rxjava3.disposables.Disposable;

import javax.inject.Inject;

public class UpdateCheckerService extends Service {

    private NotificationHelper notificationHelper;
    private AppRepository repository;
    private Disposable disposable;

    @Inject
    void setDependencies(NotificationHelper helper, AppRepository repo) {
        notificationHelper = helper;
        repository = repo;
    }

    @Override
    public void onCreate() {
        ((UpdaterApplication) getApplication()).getComponent().inject(this);
        disposable = repository.getResponsePublisher()
            .map(response -> response.getStatus())
            .filter(status -> status != 0 && status != REFRESHING)
            .subscribe(status -> {
                if (status == NEW_UPDATE) {
                    notifyUser(R.string.notify_new_update,
                        R.string.notify_new_update_desc);
                } else if (status == REFRESH_FAILED) {
                    notifyUser(R.string.notify_refresh_failed,
                        R.string.notify_refresh_failed_desc);
                }
                stopSelf();
            });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        repository.fetchBuildInfo();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private void notifyUser(int titleId, int descId) {
        notificationHelper.showCancellableNotification(titleId, descId);
    }
}
