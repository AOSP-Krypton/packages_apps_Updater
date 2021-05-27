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

package com.krypton.updater.model.room;

import static androidx.room.OnConflictStrategy.REPLACE;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.TABLE_DOWNLOAD_INFO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import io.reactivex.rxjava3.core.Flowable;

import java.util.UUID;

@Dao
public interface DownloadStatusDao {

    @Query("SELECT * FROM " + TABLE_DOWNLOAD_INFO)
    DownloadStatusEntity getCurrentStatus();

    @Query("SELECT * FROM " + TABLE_DOWNLOAD_INFO)
    Flowable<DownloadStatusEntity> getCurrentStatusFlowable();

    @Query("SELECT status FROM " + TABLE_DOWNLOAD_INFO)
    int getStatus();

    @Query("SELECT id FROM " + TABLE_DOWNLOAD_INFO)
    UUID getDownloadId();

    @Query("UPDATE " + TABLE_DOWNLOAD_INFO + " SET id = :id")
    void updateDownloadId(UUID id);

    @Insert(onConflict = REPLACE)
    void insert(DownloadStatusEntity entity);

    @Query("UPDATE " + TABLE_DOWNLOAD_INFO + " SET status = :status")
    void updateStatus(int status);

    @Query("UPDATE " + TABLE_DOWNLOAD_INFO + " SET downloaded_size = :size, progress = :progress")
    void updateProgress(long size, int progress);

    @Query("DELETE FROM " + TABLE_DOWNLOAD_INFO)
    void deleteTable();
}
