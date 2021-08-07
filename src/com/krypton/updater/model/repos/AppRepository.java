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
import static android.graphics.Typeface.BOLD;
import static android.os.PowerManager.REBOOT_REQUESTED_BY_DEVICE_OWNER;
import static android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
import static com.krypton.updater.util.Constants.DOWNLOAD_PENDING;
import static com.krypton.updater.util.Constants.CHANGELOG_UP_TO_DATE;
import static com.krypton.updater.util.Constants.FETCHING_CHANGELOG;
import static com.krypton.updater.util.Constants.FINISHED;
import static com.krypton.updater.util.Constants.NEW_CHANGELOG;
import static com.krypton.updater.util.Constants.NEW_UPDATE;
import static com.krypton.updater.util.Constants.REBOOT_PENDING;
import static com.krypton.updater.util.Constants.REFRESH_INTERVAL_KEY;
import static com.krypton.updater.util.Constants.REFRESHING;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static java.util.concurrent.TimeUnit.DAYS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.SparseArray;

import com.krypton.updater.model.room.AppDatabase;
import com.krypton.updater.model.room.BuildInfoDao;
import com.krypton.updater.model.room.BuildInfoEntity;
import com.krypton.updater.model.room.GlobalStatusDao;
import com.krypton.updater.model.room.GlobalStatusEntity;
import com.krypton.updater.model.room.ChangelogDao;
import com.krypton.updater.model.room.ChangelogEntity;
import com.krypton.updater.model.data.BuildInfo;
import com.krypton.updater.model.data.Changelog;
import com.krypton.updater.model.data.DownloadManager;
import com.krypton.updater.model.data.GithubApiHelper;
import com.krypton.updater.model.data.Response;
import com.krypton.updater.model.data.UpdateManager;
import com.krypton.updater.R;
import com.krypton.updater.services.UpdateCheckerService;
import com.krypton.updater.util.Utils;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.TreeMap;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppRepository implements OnSharedPreferenceChangeListener {
    private static final String TAG = "AppRepository";
    private static final int REQUEST_CODE_CHECK_UPDATE = 1003;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");
    private final AlarmManager alarmManager;
    private final Context context;
    private final ExecutorService executor;
    private final AppDatabase database;
    private final BuildInfoDao buildInfoDao;
    private final ChangelogDao changelogDao;
    private final GlobalStatusDao globalStatusDao;
    private final BehaviorProcessor<Response> otaResponsePublisher,
        changelogResponsePublisher;
    private final SharedPreferences sharedPrefs;
    private final DownloadManager downloadManager;
    private final UpdateManager updateManager;
    private final GithubApiHelper githubApiHelper;
    private final String changelogPrefix;
    private final SpannableStringBuilder stringBuilder;
    private Future<Boolean> fetching;

    @Inject
    public AppRepository(Context context, AppDatabase database,
            ExecutorService executor, SharedPreferences sharedPrefs,
            DownloadManager downloadManager, UpdateManager updateManager,
            GithubApiHelper githubApiHelper) {
        this.context = context;
        this.database = database;
        this.executor = executor;
        this.sharedPrefs = sharedPrefs;
        this.downloadManager = downloadManager;
        this.updateManager = updateManager;
        this.githubApiHelper = githubApiHelper;
        globalStatusDao = database.getGlobalStatusDao();
        buildInfoDao = database.getBuildInfoDao();
        changelogDao = database.getChangelogDao();
        alarmManager = context.getSystemService(AlarmManager.class);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        otaResponsePublisher = BehaviorProcessor.createDefault(new Response(0));
        changelogResponsePublisher = BehaviorProcessor.createDefault(new Response(0));
        changelogPrefix = context.getString(R.string.changelog).concat(" - ");
        stringBuilder = new SpannableStringBuilder();
        updateFromDatabase();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(REFRESH_INTERVAL_KEY)) {
            setAlarm();
        }
    }

    public BehaviorProcessor<Response> getOTAResponsePublisher() {
        return otaResponsePublisher;
    }

    public BehaviorProcessor<Response> getChangelogResponsePublisher() {
        return changelogResponsePublisher;
    }

    public Flowable<GlobalStatusEntity> getCurrentStatusFlowable() {
        return globalStatusDao.getCurrentStatusFlowable();
    }

    public void fetchBuildInfo() {
        if (fetching != null && !fetching.isDone()) {
            return;
        }
        otaResponsePublisher.onNext(new Response(REFRESHING));
        fetching = executor.submit(() -> {
            final Response response = githubApiHelper.parseOTAInfo();
            otaResponsePublisher.onNext(response);
            final BuildInfo buildInfo = (BuildInfo) response.getResponseBody();
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
            return response.getStatus() == NEW_UPDATE;
        });
    }

    public void fetchChangelog() {
        executor.execute(() -> {
            try {
                if (fetching == null || !fetching.get()) {
                    return;
                }
            } catch(InterruptedException|ExecutionException e) {
                Log.e(TAG, "Exception when getting ota response status", e);
                return;
            }
            changelogResponsePublisher.onNext(new Response(FETCHING_CHANGELOG));
            final List<ChangelogEntity> currentList = changelogDao.getChangelogList();
            final Response response = githubApiHelper.parseChangelogInfo(
                new TreeMap<>(currentList.stream()
                    .collect(Collectors.toMap(entity -> entity.date,
                        entity -> new Changelog(entity)))
                )
            );
            final int status = response.getStatus();
            if (status == NEW_CHANGELOG || status == CHANGELOG_UP_TO_DATE) {
                final TreeMap<Date, Changelog> mappedChangelog = (TreeMap) response.getResponseBody();
                if (status == NEW_CHANGELOG) {
                    changelogDao.clear();
                    changelogDao.insert(mappedChangelog.values().stream()
                        .map(changelog -> changelog.toEntity())
                        .collect(Collectors.toList()));
                }
                stringBuilder.clear();
                mappedChangelog.values().stream()
                    .forEach(changelog -> addChangelogToBuilder(stringBuilder,
                        changelog.getDate(), changelog.getChangelog()));
                changelogResponsePublisher.onNext(new Response(stringBuilder, NEW_CHANGELOG));
            } else {
                changelogResponsePublisher.onNext(response);
            }
        });
    }

    public void resetStatus() {
        executor.execute(() -> {
            // Only delete data if no ongoing downloads or updates are there
            if (!downloadManager.isDownloading() && !updateManager.isUpdating()) {
                final Response empty = new Response(0);
                otaResponsePublisher.onNext(empty);
                changelogResponsePublisher.onNext(empty);
                changelogDao.clear();
                database.getDownloadStatusDao().deleteTable();
                final GlobalStatusEntity entity = globalStatusDao.getCurrentStatus();
                if (entity != null) {
                    if (entity.tag != null) {
                        buildInfoDao.deleteByTag(entity.tag);
                    }
                    globalStatusDao.delete(entity.rowId);
                }
                globalStatusDao.insert(new GlobalStatusEntity());
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
        if (updateManager.getCurrentStatusCode() == FINISHED) {
            return;
        }
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
                long refreshTimeout =  DAYS.toMillis(sharedPrefs.getInt(
                    REFRESH_INTERVAL_KEY, 7));
                long timeSinceUpdate = System.currentTimeMillis() - entity.entryDate;
                if (timeSinceUpdate < refreshTimeout) {
                    final BuildInfoEntity buildInfoEntity = buildInfoDao.findByTag(entity.tag);
                    if (buildInfoEntity != null) {
                        otaResponsePublisher.onNext(new Response(
                            buildInfoEntity.toBuildInfo(), NEW_UPDATE));
                        changelogDao.getChangelogList().stream()
                            .forEach(cgEntity -> addChangelogToBuilder(stringBuilder,
                                cgEntity.date, cgEntity.changelog));
                        changelogResponsePublisher.onNext(new Response(stringBuilder, NEW_CHANGELOG));
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
        final PendingIntent alarmIntent = PendingIntent.getService(
            context, REQUEST_CODE_CHECK_UPDATE, new Intent(context, UpdateCheckerService.class),
                FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
        alarmManager.setInexactRepeating(ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval, interval, alarmIntent);
    }

    private void addChangelogToBuilder(SpannableStringBuilder builder,
            Date date, String changelog) {
        int startIndex = builder.length();
        builder.append(changelogPrefix);
        builder.append(dateFormat.format(date));
        builder.setSpan(new StyleSpan(BOLD), startIndex, builder.length(),
            SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append("\n\n");
        builder.append(changelog);
        builder.append("\n");
    }
}
