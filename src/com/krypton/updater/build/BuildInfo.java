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

package com.krypton.updater.build;

import android.os.Bundle;

import com.krypton.updater.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BuildInfo {

    private Bundle bundle;
    private String fileName, md5sum;
    private long fileSize;

    public BuildInfo() {
        if (bundle == null) {
            bundle = new Bundle();
        }
    }

    public void setVersion(String version) {
        bundle.putString(Utils.BUILD_VERSION, version);
    }

    public void setBuildDate(long date) {
        bundle.putString(Utils.BUILD_DATE, String.valueOf(date));
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
        bundle.putString(Utils.BUILD_NAME, fileName);
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
        bundle.putLong(Utils.BUILD_SIZE, fileSize);
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
        bundle.putString(Utils.BUILD_MD5SUM, md5sum);
    }

    public Bundle getBundle() {
        return bundle;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean checkMd5sum(File file) {
        try (FileInputStream inStream = new FileInputStream(file)) {
            byte[] buffer = new byte[Utils.MB];
            int bytesRead = 0;
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            while ((bytesRead = inStream.read(buffer)) != -1) {
                md5Digest.update(buffer, 0, bytesRead);
            }
            StringBuilder builder = new StringBuilder();
            for (byte b: md5Digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString().equals(md5sum);
        } catch (IOException e) {
            Utils.log(e);
        } catch (NoSuchAlgorithmException e) {
            // I am pretty sure MD5 exists
        }
        return false;
    }
}
