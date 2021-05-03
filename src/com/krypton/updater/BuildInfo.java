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

package com.krypton.updater;

import android.os.Bundle;

public final class BuildInfo {

    private Bundle bundle;

    public BuildInfo(String version, String timestamp,
            String fileName, long size, String md5sum) {
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putString(Utils.BUILD_VERSION, version);
        bundle.putString(Utils.BUILD_TIMESTAMP, timestamp);
        bundle.putString(Utils.BUILD_NAME, fileName);
        bundle.putLong(Utils.BUILD_SIZE, size);
        bundle.putString(Utils.BUILD_MD5SUM, md5sum);
    }

    public Bundle getBundle() {
        return bundle;
    }

    public String getVersion() {
        return bundle.getString(Utils.BUILD_VERSION);
    }

    public String getTimestamp() {
        return bundle.getString(Utils.BUILD_TIMESTAMP);
    }

    public String getFileName() {
        return bundle.getString(Utils.BUILD_NAME);
    }

    public long getFileSize() {
        return bundle.getLong(Utils.BUILD_SIZE);
    }

    public String getMd5sum() {
        return bundle.getString(Utils.BUILD_MD5SUM);
    }
}
