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

public final class BuildInfo {

    private String version, url, fileName, md5;
    private long date, fileSize;

    public BuildInfo setVersion(String version) {
        this.version = version;
        return this;
    }

    public BuildInfo setDate(long date) {
        this.date = date;
        return this;
    }

    public BuildInfo setURL(String url) {
        this.url = url;
        return this;
    }

    public BuildInfo setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public BuildInfo setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public BuildInfo setMd5(String md5) {
        this.md5 = md5;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public long getDate() {
        return date;
    }

    public String getURL() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMd5() {
        return md5;
    }
}
