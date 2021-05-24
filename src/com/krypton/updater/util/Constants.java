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

package com.krypton.updater.util;

// Constants needed application-wide
public class Constants {
    // For build info
    public static final String BUILD_INFO = "build-info";
    public static final String BUILD_VERSION = "version";
    public static final String BUILD_DATE = "date";
    public static final String BUILD_NAME = "filename";
    public static final String BUILD_SIZE = "filesize";
    public static final String BUILD_MD5SUM = "md5";

    // Restore download progress
    public static final String DOWNLOADED_SIZE = "downloadedSize";
    public static final String DOWNLOAD_STARTED = "downloadStarted";
    public static final String DOWNLOAD_PAUSED = "downloadPaused";
    public static final String DOWNLOAD_FINISHED = "downloadFinished";

    // Restore update engine progress
    public static final String LOCAL_UPGRADE_MODE = "localUpgradeMode";
    public static final String UPDATE_STARTED = "updateStarted";
    public static final String UPDATE_PAUSED = "updatePaused";
    public static final String UPDATE_FINISHED = "updateFinished";
    public static final String UPDATE_EXIT_CODE = "updateExitCode";
    public static final String UPDATE_STATUS = "updateStatus";
    public static final String UPDATE_PROGRESS = "updateProgress";

    // Error codes for apply update failure
    public static final int APPLY_PAYLOAD_FAILED = 101;
    public static final int FILE_INVALID = 102;
    public static final int FILE_EXCEPTION = 103;

    // 1 MB in bytes
    public static final int MB = 1048576;
}
