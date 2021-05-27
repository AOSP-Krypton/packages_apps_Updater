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
import static com.krypton.updater.util.Constants.TABLE_GLOBAL_STATUS;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import io.reactivex.rxjava3.core.Flowable;

import java.util.List;
import java.util.UUID;

@Dao
public interface GlobalStatusDao {

    @Query("SELECT * FROM " + TABLE_GLOBAL_STATUS + " ORDER BY rowid DESC LIMIT 1")
    GlobalStatusEntity getCurrentStatus();

    @Query("SELECT * FROM " + TABLE_GLOBAL_STATUS + " ORDER BY rowid DESC LIMIT 1")
    Flowable<GlobalStatusEntity> getCurrentStatusFlowable();

    @Query("SELECT * FROM " + TABLE_GLOBAL_STATUS)
    List<GlobalStatusEntity> getAll();

    @Query("DELETE FROM " + TABLE_GLOBAL_STATUS + " WHERE rowid LIKE :id")
    void delete(int id);

    @Insert(onConflict = REPLACE)
    void insert(GlobalStatusEntity entity);

    @Query("UPDATE " + TABLE_GLOBAL_STATUS + " SET status = :status WHERE rowid LIKE (SELECT rowid FROM "
                + TABLE_GLOBAL_STATUS + " ORDER BY rowid DESC LIMIT 1)")
    void updateCurrentStatus(int status);

    @Query("UPDATE " + TABLE_GLOBAL_STATUS + " SET local_upgrade_file = :fileName WHERE rowid LIKE (SELECT rowid FROM "
                + TABLE_GLOBAL_STATUS + " ORDER BY rowid DESC LIMIT 1)")
    void setLocalUpgradeFileName(String fileName);
}
