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

import static androidx.work.BackoffPolicy.LINEAR;
import static androidx.work.NetworkType.CONNECTED;
import static androidx.work.OneTimeWorkRequest.MIN_BACKOFF_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static com.krypton.updater.util.Constants.MB;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.BUILD_NAME;
import static com.krypton.updater.util.Constants.BUILD_SIZE;
import static com.krypton.updater.util.Constants.BUILD_URL;
import static com.krypton.updater.util.Constants.DOWNLOADING;
import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.CANCELLED;
import static com.krypton.updater.util.Constants.FAILED;
import static com.krypton.updater.util.Constants.FINISHED;
import static com.krypton.updater.util.Constants.DOWNLOAD_PENDING;

import android.content.Context;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.model.room.*;
import com.krypton.updater.R;
import com.krypton.updater.model.data.DownloadManager;
import com.krypton.updater.util.Utils;
import com.krypton.updater.workers.DownloadWorker;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DownloadRepository {

    private final Context context;
    private final DownloadStatusDao downloadStatusDao;
    private final BuildInfoDao buildInfoDao;
    private final GlobalStatusDao globalStatusDao;
    private final ExecutorService executor;
    private final AppDatabase database;
    private final WorkManager workManager;
    private final DownloadManager downloadManager;

    @Inject
    public DownloadRepository(Context context,
            ExecutorService executor, AppDatabase database) {
        this.context = context;
        this.database = database;
        this.executor = executor;
        workManager = WorkManager.getInstance(context);
        buildInfoDao = database.getBuildInfoDao();
        downloadStatusDao = database.getDownloadStatusDao();
        globalStatusDao = database.getGlobalStatusDao();
        downloadManager = new DownloadManager(workManager,
            buildInfoDao, downloadStatusDao);
    }

    public void startDownload() {
        executor.execute(() -> {
            clearCache();
            downloadManager.start();
            setGlobalStatus(DOWNLOADING);
        });
    }

    public void pauseDownload() {
        executor.execute(() -> downloadManager.pauseOrResume());
    }

    public void cancelDownload() {
        executor.execute(() -> downloadManager.cancel());
        clearData();
    }

    public Flowable<DownloadStatusEntity> getDatabaseFlowable() {
        return downloadStatusDao.getCurrentStatusFlowable();
    }

    public PublishSubject<UUID> getUUIDSubject() {
        return downloadManager.getUUIDSubject();
    }

    public WorkManager getWorkManager() {
        return workManager;
    }

    public ProgressInfo getProgressInfo(DownloadStatusEntity entity) {
        String status = "";
        switch (entity.status) {
            case INDETERMINATE:
                status = getString(R.string.waiting);
                break;
            case DOWNLOADING:
                status = getString(R.string.downloading);
                break;
            case PAUSED:
                status = getString(R.string.download_paused);
                break;
            case FINISHED:
                status = getString(R.string.download_finished);
                break;
            case FAILED:
                status = getString(R.string.download_failed);
                break;
        }
        return new ProgressInfo()
            .setProgress(entity.progress)
            .setIndeterminate(entity.status == INDETERMINATE)
            .setExtras(String.format("%d/%d MB",
                (int) (entity.downloadedSize / MB), (int) entity.fileSize / MB))
            .setStatus(status);
    }

    public ProgressInfo getProgressInfo(State state) {
        switch (state) {
            case ENQUEUED:
                return new ProgressInfo()
                    .setIndeterminate(state == State.ENQUEUED)
                    .setStatus(getString(R.string.waiting));
            case CANCELLED:
                executor.execute(() -> {
                    if (!downloadManager.isPaused()) {
                        clearData();
                    }
                });
        }
        return null;
    }

    private void clearData() {
        executor.execute(() -> {
            downloadManager.cancel();
            workManager.pruneWork();
            setGlobalStatus(DOWNLOAD_PENDING);
            clearCache();
        });
    }

    private void clearCache() {
        for (File file: context.getExternalCacheDir().listFiles()) {
            file.delete();
        }
    }

    private void setGlobalStatus(int status) {
        globalStatusDao.updateCurrentStatus(status);
    }

    private String getString(int id) {
        return context.getString(id);
    }
}
