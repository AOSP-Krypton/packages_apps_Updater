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

package com.krypton.updater.model.repos;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.INTERVAL_DAY;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_NO_CREATE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.os.PowerManager.REBOOT_REQUESTED_BY_DEVICE_OWNER;
import static com.krypton.updater.util.Constants.DOWNLOAD_PENDING;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static com.krypton.updater.util.Constants.NEW_UPDATE;
import static com.krypton.updater.util.Constants.REFRESHING;
import static com.krypton.updater.util.Constants.REFRESH_INTERVAL_KEY;
import static com.krypton.updater.util.Constants.REBOOT_PENDING;
import static java.util.concurrent.TimeUnit.DAYS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.preference.PreferenceManager;

import com.krypton.updater.model.room.AppDatabase;
import com.krypton.updater.model.room.BuildInfoDao;
import com.krypton.updater.model.room.BuildInfoEntity;
import com.krypton.updater.model.room.GlobalStatusDao;
import com.krypton.updater.model.room.GlobalStatusEntity;
import com.krypton.updater.model.data.BuildInfo;
import com.krypton.updater.model.data.Response;
import com.krypton.updater.services.UpdateCheckerService;
import com.krypton.updater.model.data.JSONParser;
import com.krypton.updater.util.Utils;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppRepository implements OnSharedPreferenceChangeListener {
    private static final int REQUEST_CODE_CHECK_UPDATE = 1003;
    private final AlarmManager alarmManager;
    private final Context context;
    private final ExecutorService executor;
    private final JSONParser parser;
    private final AppDatabase database;
    private final BuildInfoDao buildInfoDao;
    private final GlobalStatusDao globalStatusDao;
    private final BehaviorProcessor<Response> responsePublisher;
    private final SharedPreferences sharedPrefs;
    private UUID tag;
    private Future fetching;

    @Inject
    public AppRepository(Context context, AppDatabase database,
            ExecutorService executor, JSONParser parser,
            SharedPreferences sharedPrefs) {
        this.context = context;
        this.database = database;
        this.executor = executor;
        this.parser = parser;
        this.sharedPrefs = sharedPrefs;
        globalStatusDao = database.getGlobalStatusDao();
        buildInfoDao = database.getBuildInfoDao();
        alarmManager = context.getSystemService(AlarmManager.class);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        responsePublisher = BehaviorProcessor.createDefault(new Response(0));
        updateFromDatabase();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(REFRESH_INTERVAL_KEY)) {
            setAlarm();
        }
    }

    public BehaviorProcessor<Response> getResponsePublisher() {
        return responsePublisher;
    }

    public Flowable<GlobalStatusEntity> getCurrentStatusFlowable() {
        return globalStatusDao.getCurrentStatusFlowable();
    }

    public void fetchBuildInfo() {
        if (fetching != null && !fetching.isDone()) {
            return;
        }
        responsePublisher.onNext(new Response(REFRESHING));
        fetching = executor.submit(() -> {
            final Response response = parser.parse();
            responsePublisher.onNext(response);
            final BuildInfo buildInfo = response.getBuildInfo();
            if (buildInfo != null) {
                long date = buildInfo.getDate();
                GlobalStatusEntity globalStatusEntity = globalStatusDao.getCurrentStatus();
                if (globalStatusEntity == null) {
                    globalStatusEntity = new GlobalStatusEntity();
                }
                if (globalStatusEntity.buildDate != date) {
                    globalStatusEntity.entryDate = System.currentTimeMillis();
                    globalStatusEntity.buildDate = date;
                    if (globalStatusEntity.tag == null) {
                        globalStatusEntity.tag = UUID.randomUUID();
                    }
                }
                globalStatusEntity.status = DOWNLOAD_PENDING;
                if (buildInfoDao.findByMd5(buildInfo.getMd5()) == null) {
                    final BuildInfoEntity buildInfoEntity = buildInfo.toEntity();
                    buildInfoEntity.tag = globalStatusEntity.tag;
                    buildInfoDao.insert(buildInfoEntity);
                }
                globalStatusDao.insert(globalStatusEntity);
            }
            setAlarm();
        });
    }

    public void resetStatus() {
        executor.execute(() -> {
            final GlobalStatusEntity entity = globalStatusDao.getCurrentStatus();
            if (entity == null) {
                return;
            }
            final int status = entity.status;
            // Only delete data if no ongoing downloads or updates are there
            if (status == DOWNLOAD_PENDING || status == UPDATE_PENDING) {
                if (entity.tag != null) {
                    buildInfoDao.deleteByTag(entity.tag);
                }
                responsePublisher.onNext(new Response(0));
                globalStatusDao.delete(entity.rowId);
                database.getDownloadStatusDao().deleteTable();
                globalStatusDao.insert(new GlobalStatusEntity());
                globalStatusDao.setLocalUpgradeFileName(null);
            }
        });
    }

    public void resetStatusAndReboot() {
        executor.execute(() -> {
            globalStatusDao.insert(new GlobalStatusEntity());
            database.getDownloadStatusDao().deleteTable();
            final PowerManager powerManager = context.getSystemService(PowerManager.class);
            if (powerManager != null) {
                powerManager.reboot(REBOOT_REQUESTED_BY_DEVICE_OWNER);
            }
        });
    }

    public void resetStatusIfNotDone() {
        executor.execute(() -> {
            final GlobalStatusEntity entity = globalStatusDao.getCurrentStatus();
            if (entity != null && entity.status == REBOOT_PENDING) {
                globalStatusDao.insert(new GlobalStatusEntity());
                database.getDownloadStatusDao().deleteTable();
            }
        });
    }

    private void updateFromDatabase() {
        executor.execute(() -> {
            final GlobalStatusEntity entity = globalStatusDao.getCurrentStatus();
            if (entity == null) {
                return;
            }
            if (entity.buildDate > Utils.getBuildDate()) {
                long refreshTimeout =  DAYS.toMillis(sharedPrefs.getInt(REFRESH_INTERVAL_KEY, 7));
                long timeSinceUpdate = System.currentTimeMillis() - entity.entryDate;
                if (timeSinceUpdate < refreshTimeout) {
                    final BuildInfoEntity buildInfoEntity = buildInfoDao.findByTag(entity.tag);
                    if (buildInfoEntity != null) {
                        responsePublisher.onNext(new Response(buildInfoEntity.toBuildInfo(), NEW_UPDATE));
                    }
                }
            } else {
                // Stored data is too old, reset to prompt user to do a manual refresh
                resetStatus();
            }
        });
    }

    private void setAlarm() {
        long interval = sharedPrefs.getInt(REFRESH_INTERVAL_KEY, 7) * INTERVAL_DAY;
        final PendingIntent alarmIntent = PendingIntent.getService(context, REQUEST_CODE_CHECK_UPDATE,
            new Intent(context, UpdateCheckerService.class), FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        alarmManager.setInexactRepeating(ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval, interval, alarmIntent);
    }
}
