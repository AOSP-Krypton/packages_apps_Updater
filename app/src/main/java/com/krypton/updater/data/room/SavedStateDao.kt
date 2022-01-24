/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.data.room

import androidx.room.Dao
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

@Dao
interface SavedStateDao {

    @Query("SELECT last_checked_time FROM saved_state_table ORDER BY last_checked_time DESC")
    fun getLastCheckedTime(): Flow<Long?>

    @Query("INSERT INTO saved_state_table (last_checked_time) VALUES (:time)")
    fun setLastCheckedTime(time: Long)
}