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
import static com.krypton.updater.util.Constants.TABLE_BUILD_INFO;
import static com.krypton.updater.util.Constants.BUILD_MD5;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.UUID;

@Dao
public interface BuildInfoDao {

    @Query("SELECT * FROM " + TABLE_BUILD_INFO + " WHERE tag LIKE :tag LIMIT 1")
    BuildInfoEntity findByTag(UUID tag);

    @Query("SELECT * FROM " + TABLE_BUILD_INFO + " WHERE md5 LIKE :md5 LIMIT 1")
    BuildInfoEntity findByMd5(String md5);

    @Query("SELECT * FROM " + TABLE_BUILD_INFO + " ORDER BY rowid DESC LIMIT 1")
    BuildInfoEntity getCurrentBuildInfo();

    @Insert(onConflict = REPLACE)
    void insert(BuildInfoEntity entity);
}
