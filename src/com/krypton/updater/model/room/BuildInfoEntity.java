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

import static com.krypton.updater.util.Constants.TABLE_BUILD_INFO;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.krypton.updater.model.data.BuildInfo;

import java.util.UUID;

@Fts4
@Entity(tableName = TABLE_BUILD_INFO)
public class BuildInfoEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    @Ignore
    public int rowId;

    @NonNull
    public String md5;

    @NonNull
    public UUID tag;

    public long date;
    public String version;

    @NonNull
    public String url;

    @ColumnInfo(name = "file_name")
    @NonNull
    public String fileName;

    @ColumnInfo(name = "file_size")
    public long fileSize;

    public BuildInfo toBuildInfo() {
        return new BuildInfo()
            .setVersion(version)
            .setDate(date)
            .setFileName(fileName)
            .setURL(url)
            .setMd5(md5)
            .setFileSize(fileSize);
    }
}
