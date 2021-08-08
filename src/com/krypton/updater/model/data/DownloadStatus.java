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

public final class DownloadStatus {
    private int status, progress;
    private long downloadedSize, fileSize;

    public DownloadStatus setStatus(int status) {
        this.status = status;
        return this;
    }

    public DownloadStatus setProgress(int progress) {
        this.progress = progress;
        return this;
    }

    public DownloadStatus setDownloadedSize(long downloadedSize) {
        this.downloadedSize = downloadedSize;
        return this;
    }

    public DownloadStatus setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public int getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public long getDownloadedSize() {
        return downloadedSize;
    }

    public long getFileSize() {
        return fileSize;
    }
}
