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

package com.krypton.updater.model.data;;

import static android.os.FileUtils.S_IRWXU;
import static android.os.FileUtils.S_IRWXG;

import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class OTAFileManager {
    private static final String TAG = "OTAFileManager";
    private static final String OTA_DIR = "kosp_ota";
    private static final String UPDATE_FILE = "update.zip";
    private final File otaPackageDir;
    private final File otaFile;

    @Inject
    public OTAFileManager() {
        otaPackageDir = new File(Environment.getDataDirectory(), OTA_DIR);
        checkOTADir();
        otaFile = new File(otaPackageDir, UPDATE_FILE);
    }

    public void checkOTADir() {
        if (!otaPackageDir.isDirectory()) {
            throw new RuntimeException("ota package dir " +
                otaPackageDir.getAbsolutePath() + " does not exist");
        }
        if (!(otaPackageDir.canRead() &&
                otaPackageDir.canWrite() && otaPackageDir.canExecute())) {
            throw new RuntimeException("no rwx permission for " +
                otaPackageDir.getAbsolutePath());
        }
    }

    @NonNull
    public Uri getOTAFileUri() {
        return Uri.fromFile(otaFile);
    }

    public boolean copyToOTAPackageDir(InputStream inStream) {
        if (!cleanup()) {
            return false;
        }
        try (FileOutputStream outStream = new FileOutputStream(otaFile)) {
            FileUtils.copy(inStream, outStream);
            int errno = FileUtils.setPermissions(otaFile, S_IRWXU | S_IRWXG, -1, -1);
            if (errno == 0) {
                return true;
            } else {
                Log.e(TAG, "setPermissions for " + otaFile.getAbsolutePath() +
                    " failed with errno = " + errno);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException when copying to ota dir", e);
        }
        return false;
    }

    public boolean cleanup() {
        if (otaPackageDir == null) {
            return false;
        }
        for (File file: otaPackageDir.listFiles()) {
            if (!file.delete()) {
                Log.e(TAG, "deleting " + file.getAbsolutePath() + " failed");
                return false;
            }
        }
        return true;
    }
}
