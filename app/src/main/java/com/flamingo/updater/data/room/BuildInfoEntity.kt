/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "build_info_table")
@Fts4
data class BuildInfoEntity(
    var version: String,
    var date: Long,
    @ColumnInfo(name = "pre_build_incremental")
    var preBuildIncremental: Long?,
    @ColumnInfo(name = "download_sources")
    var downloadSources: Map<String, String>,
    @ColumnInfo(name = "file_name")
    var fileName: String,
    @ColumnInfo(name = "file_size")
    var fileSize: Long,
    @ColumnInfo(name = "sha")
    var sha512: String,
) {
    @PrimaryKey(autoGenerate = true)
    @Ignore
    var rowid: Int? = null
}