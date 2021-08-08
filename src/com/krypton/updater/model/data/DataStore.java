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

import static com.krypton.updater.util.Constants.BUILD_DATE;
import static com.krypton.updater.util.Constants.BUILD_NAME;
import static com.krypton.updater.util.Constants.BUILD_SIZE;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.BUILD_URL;
import static com.krypton.updater.util.Constants.BUILD_VERSION;

import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class DataStore {

    private final SharedPreferences sharedPrefs;
    private BuildInfo buildInfo;

    @Inject
    public DataStore(SharedPreferences sharedPrefs) {
        this.sharedPrefs = sharedPrefs;
    }

    public BuildInfo getBuildInfo() {
        if (buildInfo == null) {
            final String md5 = sharedPrefs.getString(BUILD_MD5, null);
            if (md5 != null) {
                buildInfo = new BuildInfo()
                    .setVersion(sharedPrefs.getString(BUILD_VERSION, null))
                    .setDate(sharedPrefs.getLong(BUILD_DATE, 0))
                    .setURL(sharedPrefs.getString(BUILD_URL, null))
                    .setFileName(sharedPrefs.getString(BUILD_NAME, null))
                    .setFileSize(sharedPrefs.getLong(BUILD_SIZE, 0))
                    .setMd5(md5);
            }
        }
        return buildInfo;
    }

    public void updateBuildInfo(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
        sharedPrefs.edit()
            .putString(BUILD_VERSION, buildInfo.getVersion())
            .putLong(BUILD_DATE, buildInfo.getDate())
            .putString(BUILD_URL, buildInfo.getURL())
            .putString(BUILD_NAME, buildInfo.getFileName())
            .putLong(BUILD_SIZE, buildInfo.getFileSize())
            .putString(BUILD_MD5, buildInfo.getMd5())
            .commit();
    }

    public void deleteBuildInfo() {
        if (buildInfo != null) {
            buildInfo = null;
        }
        sharedPrefs.edit()
            .remove(BUILD_VERSION)
            .remove(BUILD_DATE)
            .remove(BUILD_URL)
            .remove(BUILD_NAME)
            .remove(BUILD_SIZE)
            .remove(BUILD_MD5)
            .commit();
    }
}
