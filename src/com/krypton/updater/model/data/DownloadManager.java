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

package com.krypton.updater.model.data;

import static androidx.work.BackoffPolicy.LINEAR;
import static androidx.work.NetworkType.CONNECTED;
import static androidx.work.OneTimeWorkRequest.MIN_BACKOFF_MILLIS;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.BUILD_NAME;
import static com.krypton.updater.util.Constants.BUILD_SIZE;
import static com.krypton.updater.util.Constants.BUILD_URL;
import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.CANCELLED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.krypton.updater.model.room.BuildInfoDao;
import com.krypton.updater.model.room.BuildInfoEntity;
import com.krypton.updater.model.room.DownloadStatusDao;
import com.krypton.updater.model.room.DownloadStatusEntity;
import com.krypton.updater.workers.DownloadWorker;

import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.UUID;

public class DownloadManager {

    private final Constraints constraints;
    private final BuildInfoDao buildInfoDao;
    private final DownloadStatusDao downloadStatusDao;
    private final WorkManager workManager;
    private final PublishSubject<UUID> uuidSubject;
    private UUID id;

    public DownloadManager(WorkManager workManager,
            BuildInfoDao buildInfoDao, DownloadStatusDao downloadStatusDao) {
        this.workManager = workManager;
        this.buildInfoDao = buildInfoDao;
        this.downloadStatusDao = downloadStatusDao;
        constraints = new Constraints.Builder()
            .setRequiredNetworkType(CONNECTED)
            .setRequiresStorageNotLow(true)
            .build();
        uuidSubject = PublishSubject.create();
    }

    @WorkerThread
    public void start() {
        downloadStatusDao.deleteTable();
        fetchAndEnqueueDownload();
    }

    @WorkerThread
    public void pauseOrResume() {
        if (id == null) {
            id = downloadStatusDao.getDownloadId();
        }
        if (id != null) {
            workManager.cancelWorkById(id);
            id = null;
            downloadStatusDao.updateStatus(PAUSED);
        } else {
            fetchAndEnqueueDownload();
            downloadStatusDao.updateStatus(INDETERMINATE);
        }
        downloadStatusDao.updateDownloadId(id);
    }

    @WorkerThread
    public void cancel() {
        if (id != null) {
            workManager.cancelWorkById(id);
            id = null;
            downloadStatusDao.updateDownloadId(null);
        }
        downloadStatusDao.updateStatus(CANCELLED);
    }

    @WorkerThread
    public boolean isPaused() {
        return downloadStatusDao.getStatus() == PAUSED;
    }

    public PublishSubject<UUID> getUUIDSubject() {
        return uuidSubject;
    }

    @WorkerThread
    private void resetTable(long size) {
        final DownloadStatusEntity entity = new DownloadStatusEntity();
        entity.id = id;
        entity.status = INDETERMINATE;
        entity.progress = 0;
        entity.downloadedSize = 0;
        entity.fileSize = size;
        downloadStatusDao.insert(entity);
    }

    private OneTimeWorkRequest buildRequest(BuildInfo buildInfo) {
        return new OneTimeWorkRequest.Builder(DownloadWorker.class)
            .setConstraints(constraints)
            .setInputData(new Data.Builder()
                .putString(BUILD_URL, buildInfo.getURL())
                .putString(BUILD_NAME, buildInfo.getFileName())
                .putString(BUILD_MD5, buildInfo.getMd5())
                .putLong(BUILD_SIZE, buildInfo.getFileSize())
                .build())
            .setBackoffCriteria(LINEAR, MIN_BACKOFF_MILLIS, MILLISECONDS)
            .build();
    }

    private void fetchAndEnqueueDownload() {
        final BuildInfoEntity entity = buildInfoDao.getCurrentBuildInfo();
        if (entity == null) {
            return;
        }
        final OneTimeWorkRequest downloadRequest = buildRequest(entity.toBuildInfo());
        id = downloadRequest.getId();
        workManager.enqueue(downloadRequest);
        uuidSubject.onNext(id);
        if (downloadStatusDao.getCurrentStatus() == null) {
            resetTable(entity.fileSize);
        }
    }
}
