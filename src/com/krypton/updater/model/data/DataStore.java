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

import static com.krypton.updater.util.Constants.BUILD_DATE;
import static com.krypton.updater.util.Constants.BUILD_NAME;
import static com.krypton.updater.util.Constants.BUILD_SIZE;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.BUILD_URL;
import static com.krypton.updater.util.Constants.BUILD_VERSION;
import static com.krypton.updater.util.Constants.DOWNLOAD_ID;
import static com.krypton.updater.util.Constants.DOWNLOAD_STATUS;
import static com.krypton.updater.util.Constants.DOWNLOADED_PERCENT;
import static com.krypton.updater.util.Constants.DOWNLOADED_SIZE;
import static com.krypton.updater.util.Constants.ENTRY_DATE;
import static com.krypton.updater.util.Constants.GLOBAL_STATUS;
import static com.krypton.updater.util.Constants.LOCAL_UPGRADE_FILE;
import static com.krypton.updater.util.Constants.REFRESH_INTERVAL_KEY;
import static com.krypton.updater.util.Constants.THEME_KEY;

import android.content.SharedPreferences;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class DataStore {
    private final SharedPreferences sharedPrefs;
    private final BehaviorProcessor<Integer> globalStatusProcessor;
    private final BehaviorProcessor<DownloadStatus> downloadStatusProcessor;
    private final BehaviorProcessor<String> localUpgradeFileProcessor;
    private BuildInfo buildInfo;
    private DownloadStatus downloadStatus;
    private String localUpgradeFile;
    private UUID downloadId;
    private int status;
    private long entryDate;

    @Inject
    public DataStore(SharedPreferences sharedPrefs) {
        this.sharedPrefs = sharedPrefs;
        status = sharedPrefs.getInt(GLOBAL_STATUS, 0);
        globalStatusProcessor = BehaviorProcessor.createDefault(status);
        downloadStatusProcessor = BehaviorProcessor.create();
        localUpgradeFileProcessor = BehaviorProcessor.create();
    }

    public BuildInfo getBuildInfo() {
        if (buildInfo == null) {
            final String md5 = sharedPrefs.getString(BUILD_MD5, null);
            if (md5 != null) {
                buildInfo = new BuildInfo()
                    .setVersion(sharedPrefs.getString(BUILD_VERSION, null))
                    .setDate(sharedPrefs.getLong(BUILD_DATE, 0))
                    .setURL(sharedPrefs.getString(BUILD_URL, null))
                    .setFileName(sharedPrefs.getString(BUILD_NAME, null))
                    .setFileSize(sharedPrefs.getLong(BUILD_SIZE, 0))
                    .setMd5(md5);
            }
        }
        return buildInfo;
    }

    public void updateBuildInfo(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
        sharedPrefs.edit()
            .putString(BUILD_VERSION, buildInfo.getVersion())
            .putLong(BUILD_DATE, buildInfo.getDate())
            .putString(BUILD_URL, buildInfo.getURL())
            .putString(BUILD_NAME, buildInfo.getFileName())
            .putLong(BUILD_SIZE, buildInfo.getFileSize())
            .putString(BUILD_MD5, buildInfo.getMd5())
            .commit();
    }

    public void deleteBuildInfo() {
        if (buildInfo != null) {
            buildInfo = null;
        }
        sharedPrefs.edit()
            .remove(BUILD_VERSION)
            .remove(BUILD_DATE)
            .remove(BUILD_URL)
            .remove(BUILD_NAME)
            .remove(BUILD_SIZE)
            .remove(BUILD_MD5)
            .commit();
    }

    public BehaviorProcessor<DownloadStatus> getDownloadStatusProcessor() {
        return downloadStatusProcessor;
    }

    public DownloadStatus getDownloadStatus() {
        return downloadStatus;
    }

    public int getDownloadStatusCode() {
        return downloadStatus == null ? 0 : downloadStatus.getStatus();
    }

    public void updateDownloadStatus(int status) {
        sharedPrefs.edit()
            .putInt(DOWNLOAD_STATUS, status)
            .commit();
        if (downloadStatus == null) {
            downloadStatus = new DownloadStatus();
        }
        if (downloadStatus.getFileSize() == 0) {
            downloadStatus.setFileSize(sharedPrefs.getLong(BUILD_SIZE, 0));
        }
        downloadStatusProcessor.onNext(downloadStatus.setStatus(status));
    }

    public void updateDownloadProgress(long size, int percent) {
        sharedPrefs.edit()
            .putLong(DOWNLOADED_SIZE, size)
            .putInt(DOWNLOADED_PERCENT, percent)
            .commit();
        if (downloadStatus != null) {
            downloadStatusProcessor.onNext(downloadStatus
                .setDownloadedSize(size)
                .setProgress(percent));
        }
    }

    public void deleteDownloadStatus() {
        sharedPrefs.edit()
            .remove(DOWNLOADED_SIZE)
            .remove(DOWNLOADED_PERCENT)
            .commit();
        downloadStatus = new DownloadStatus();
        downloadStatusProcessor.onNext(downloadStatus);
    }

    public UUID getCurrentDownloadId() {
        if (downloadId == null) {
            String uuid = sharedPrefs.getString(DOWNLOAD_ID, null);
            if (uuid != null) {
                downloadId = UUID.fromString(uuid);
            }
        }
        return downloadId;
    }

    public void updateDownloadId(UUID id) {
        downloadId = id;
        if (downloadId == null) {
            sharedPrefs.edit()
                .remove(DOWNLOAD_ID)
                .commit();
        } else {
            sharedPrefs.edit()
                .putString(DOWNLOAD_ID, downloadId.toString())
                .commit();
        }
    }

    public int getGlobalStatus() {
        if (status == 0) {
            status = sharedPrefs.getInt(GLOBAL_STATUS, 0);
        }
        return status;
    }

    public BehaviorProcessor<Integer> getGlobalStatusProcessor() {
        return globalStatusProcessor;
    }

    public void setGlobalStatus(int status) {
        this.status = status;
        sharedPrefs.edit()
            .putInt(GLOBAL_STATUS, status)
            .commit();
        globalStatusProcessor.onNext(status);
    }

    public void deleteGlobalStatus() {
        sharedPrefs.edit()
            .remove(GLOBAL_STATUS)
            .remove(ENTRY_DATE)
            .remove(LOCAL_UPGRADE_FILE)
            .commit();
        globalStatusProcessor.onNext(0);
    }

    public void setEntryDate(long date) {
        sharedPrefs.edit()
            .putLong(ENTRY_DATE, date)
            .commit();
        entryDate = date;
    }

    public long getEntryDate() {
        if (entryDate == 0) {
            entryDate = sharedPrefs.getLong(ENTRY_DATE, 0);
        }
        return entryDate;
    }

    public BehaviorProcessor<String> getLocalUpgradeFileProcessor() {
        return localUpgradeFileProcessor;
    }

    public String getLocalUpgradeFileName() {
        if (localUpgradeFile == null) {
            localUpgradeFile = sharedPrefs.getString(LOCAL_UPGRADE_FILE, "");
        }
        return localUpgradeFile;
    }

    public void setLocalUpgradeFileName(String name) {
        localUpgradeFile = name;
        sharedPrefs.edit()
            .putString(LOCAL_UPGRADE_FILE, localUpgradeFile)
            .commit();
        localUpgradeFileProcessor.onNext(localUpgradeFile);
    }

    public void updateThemeMode(int mode) {
        sharedPrefs.edit()
            .putInt(THEME_KEY, mode)
            .commit();
    }

    public int getAppThemeMode() {
        return sharedPrefs.getInt(THEME_KEY, 2);
    }

    public void setRefreshInterval(int days) {
        sharedPrefs.edit()
            .putInt(REFRESH_INTERVAL_KEY, days)
            .commit();
    }

    public int getRefreshInterval() {
        return sharedPrefs.getInt(REFRESH_INTERVAL_KEY, 7);
    }
}
