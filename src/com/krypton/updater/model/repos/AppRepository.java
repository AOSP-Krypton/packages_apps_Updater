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
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.graphics.Typeface.BOLD;
import static android.os.PowerManager.REBOOT_REQUESTED_BY_DEVICE_OWNER;
import static android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
import static com.krypton.updater.util.Constants.DOWNLOAD_PENDING;
import static com.krypton.updater.util.Constants.FINISHED;
import static com.krypton.updater.util.Constants.REBOOT_PENDING;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static java.util.concurrent.TimeUnit.DAYS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;

import com.krypton.updater.model.data.BuildInfo;
import com.krypton.updater.model.data.ChangelogInfo;
import com.krypton.updater.model.data.DataStore;
import com.krypton.updater.model.data.DownloadManager;
import com.krypton.updater.model.data.ResponseCode;
import com.krypton.updater.model.data.GithubApiHelper;
import com.krypton.updater.model.data.Response;
import com.krypton.updater.model.data.UpdateManager;
import com.krypton.updater.R;
import com.krypton.updater.services.UpdateCheckerService;
import com.krypton.updater.util.Utils;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppRepository {
    private static final String TAG = "AppRepository";
    private static final boolean DEBUG = false;
    private static final int REQUEST_CODE_CHECK_UPDATE = 1003;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");

    private final AlarmManager alarmManager;
    private final Context context;
    private final ExecutorService executor;
    private final BehaviorProcessor<Response> otaResponsePublisher, changelogResponsePublisher;
    private final DownloadManager downloadManager;
    private final UpdateManager updateManager;
    private final GithubApiHelper githubApiHelper;
    private final DataStore dataStore;
    private final String changelogPrefix;

    private Future fetchingBuildInfo, fetchingChangelog;

    @Inject
    public AppRepository(
        Context context,
        ExecutorService executor,
        DownloadManager downloadManager,
        UpdateManager updateManager,
        GithubApiHelper githubApiHelper,
        DataStore dataStore
    ) {
        this.context = context;
        this.executor = executor;
        this.downloadManager = downloadManager;
        this.updateManager = updateManager;
        this.githubApiHelper = githubApiHelper;
        this.dataStore = dataStore;
        alarmManager = context.getSystemService(AlarmManager.class);
        otaResponsePublisher = BehaviorProcessor.createDefault(
            new Response(null, ResponseCode.EMPTY_RESPONSE));
        changelogResponsePublisher = BehaviorProcessor.createDefault(
            new Response(null, ResponseCode.EMPTY_RESPONSE));
        changelogPrefix = context.getString(R.string.changelog).concat(" - ");
    }

    public BehaviorProcessor<Response> getOTAResponsePublisher() {
        return otaResponsePublisher;
    }

    public BehaviorProcessor<Response> getChangelogResponsePublisher() {
        return changelogResponsePublisher;
    }

    public BehaviorProcessor<Integer> getGlobalStatusProcessor() {
        return dataStore.getGlobalStatusProcessor();
    }

    public BehaviorProcessor<String> getLocalUpgradeFileProcessor() {
        return dataStore.getLocalUpgradeFileProcessor();
    }

    public void fetchBuildInfo() {
        if (fetchingBuildInfo != null && !fetchingBuildInfo.isDone()) {
            fetchingBuildInfo.cancel(true);
        }
        logD("fetching build info");
        otaResponsePublisher.onNext(new Response(null, ResponseCode.FETCHING));
        logD("submitting job to the executor");
        fetchingBuildInfo = executor.submit(() -> {
            logD("started fetching build info");
            final BuildInfo buildInfo = githubApiHelper.getBuildInfo(Utils.getDevice());
            logD("buildInfo = " + buildInfo);
            ResponseCode code = ResponseCode.EMPTY_RESPONSE;
            if (buildInfo != null) {
                if (buildInfo.getDate() > Utils.getBuildDate()) {
                    dataStore.updateBuildInfo(buildInfo);
                    dataStore.setEntryDate(System.currentTimeMillis());
                    dataStore.setGlobalStatus(DOWNLOAD_PENDING);
                    code = ResponseCode.NEW_DATA;
                } else {
                    code = ResponseCode.UP_TO_DATE;
                }
            } else {
                code = ResponseCode.FAILED;
            }
            logD("code = " + code);
            setAlarm(DAYS.toMillis(getRefreshInterval()));
            otaResponsePublisher.onNext(new Response(buildInfo, code));
        });
    }

    public void fetchChangelog() {
        if (fetchingChangelog != null && !fetchingChangelog.isDone()) {
            fetchingChangelog.cancel(true);
        }
        logD("fetching changelog");
        changelogResponsePublisher.onNext(new Response(null, ResponseCode.FETCHING));
        fetchingChangelog = executor.submit(() -> {
            final List<ChangelogInfo> fetchedList = githubApiHelper.getChangelogs(
                Utils.getDevice(), Utils.getBuildDate()/** dateLowerBound in millis */);
            if (fetchedList != null) {
                final SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                fetchedList.forEach(changelog -> addChangelogToBuilder(
                    stringBuilder, changelog.getDate(), changelog.getChangelog()));
                changelogResponsePublisher.onNext(new Response(stringBuilder,
                    ResponseCode.NEW_DATA));
            } else {
                changelogResponsePublisher.onNext(new Response(null,
                    ResponseCode.FAILED));
            }
        });
    }

    public void resetStatus() {
        executor.execute(() -> {
            // Only delete data if no ongoing downloads or updates are there
            if (!downloadManager.isDownloading() && !updateManager.isUpdating()) {
                otaResponsePublisher.onNext(new Response(null,
                    ResponseCode.EMPTY_RESPONSE));
                changelogResponsePublisher.onNext(new Response(null,
                    ResponseCode.EMPTY_RESPONSE));
                dataStore.setLocalUpgradeFileName("");
                dataStore.deleteBuildInfo();
                dataStore.deleteDownloadStatus();
                dataStore.deleteGlobalStatus();
                updateManager.userInitiatedReset();
            }
        });
    }

    public void resetStatusAndReboot() {
        executor.execute(() -> {
            dataStore.setLocalUpgradeFileName("");
            dataStore.deleteBuildInfo();
            dataStore.deleteDownloadStatus();
            dataStore.deleteGlobalStatus();
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
            if (dataStore.getGlobalStatus() == REBOOT_PENDING) {
                dataStore.setLocalUpgradeFileName("");
                dataStore.deleteBuildInfo();
                dataStore.deleteDownloadStatus();
                dataStore.deleteGlobalStatus();
            }
        });
    }

    public int getAppThemeMode() {
        return dataStore.getAppThemeMode();
    }

    public int getRefreshInterval() {
        return dataStore.getRefreshInterval();
    }

    public void updateRefreshInterval(int days) {
        dataStore.setRefreshInterval(days);
        setAlarm(DAYS.toMillis(days));
    }

    public void updateThemeInDataStore(int mode) {
        dataStore.updateThemeMode(mode);
    }

    private void setAlarm(long interval) {
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

    private static void logD(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
