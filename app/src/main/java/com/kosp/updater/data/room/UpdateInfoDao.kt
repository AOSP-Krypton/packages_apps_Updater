/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.kosp.updater.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateInfoDao {
    @Query("SELECT COUNT(*) FROM build_info_table")
    fun entityCount(): Int

    @Query("SELECT * FROM build_info_table ORDER BY date DESC LIMIT 1")
    fun getBuildInfo(): Flow<BuildInfoEntity?>

    @MapInfo(keyColumn = "date", valueColumn = "changelog")
    @Query("SELECT changelog_table.date AS date, changelog_table.changelog AS changelog FROM changelog_table")
    fun getChangelogs(): Flow<Map<Long, String?>?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBuildInfo(buildInfoEntity: BuildInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChangelog(changelogEntity: List<ChangelogEntity>)

    @Query("DELETE FROM build_info_table")
    fun clearBuildInfo()

    @Query("DELETE FROM changelog_table")
    fun clearChangelogs()
}