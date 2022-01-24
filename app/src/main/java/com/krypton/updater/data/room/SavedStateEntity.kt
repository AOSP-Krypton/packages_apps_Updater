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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "saved_state_table")
@Fts4
data class SavedStateEntity(
    @ColumnInfo(name = "last_checked_time")
    var lastCheckedTime: Long,
) {
    @PrimaryKey(autoGenerate = true)
    @Ignore
    var rowid: Int? = null
}