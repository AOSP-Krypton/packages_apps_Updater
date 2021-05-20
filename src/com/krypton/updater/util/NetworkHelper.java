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

import android.net.Network;

import com.krypton.updater.callbacks.NetworkHelperCallbacks;
import com.krypton.updater.build.BuildInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.net.URL;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

public class NetworkHelper {

    private static final String BUILD_INFO_SOURCE_URL = "https://raw.githubusercontent.com/AOSP-Krypton/official_devices_ota/A11/";
    private static final String DOWNLOAD_SOURCE_URL = "https://sourceforge.net/projects/kosp/files/KOSP-A11-Releases/";
    private BuildInfo buildInfo;
    private URL downloadUrl;
    private boolean setUrl = false;
    private HttpsURLConnection dlConnection;
    private FileOutputStream outStream;
    private FileChannel fileChannel;
    private ReadableByteChannel rByteChannel;
    private NetworkHelperCallbacks callback;

    public void setListener(NetworkHelperCallbacks callback) {
        this.callback = callback;
    }

    public void setDownloadUrl() {
        try {
            downloadUrl = new URL(String.format("%s%s/%s",
                DOWNLOAD_SOURCE_URL, Utils.getDevice(), buildInfo.getFileName()));
            setUrl = true;
        } catch(MalformedURLException e) {
            Utils.log(e);
        }
    }

    public BuildInfo fetchBuildInfo() throws IOException, JSONException {
        StringBuilder builder = new StringBuilder();
        InputStream rawStream;
        String device = Utils.getDevice();
        try {
            rawStream = new URL(String.format("%s%s/%s.json",
                BUILD_INFO_SOURCE_URL, device, device)).openStream();
        } catch(MalformedURLException e) {
            return null;
        }

        BufferedReader buffReader = new BufferedReader(new InputStreamReader(rawStream));
        String line;
        while ((line = buffReader.readLine()) != null) {
            builder.append(line);
        }
        buffReader.close();
        rawStream.close();

        JSONObject jsonObj = new JSONObject(builder.toString()).getJSONObject(Utils.BUILD_INFO);
        String version = jsonObj.getString(Utils.BUILD_VERSION);
        long date = jsonObj.getLong(Utils.BUILD_DATE);
        float currVersion = Float.parseFloat(Utils.getVersion().substring(1));
        float newVersion = Float.parseFloat(version.substring(1));
        if (newVersion > currVersion || date > Long.parseLong(Utils.getBuildDate())) {
            setUrl = false;
            buildInfo = new BuildInfo();
            buildInfo.setVersion(version);
            buildInfo.setBuildDate(date);
            buildInfo.setFileName(jsonObj.getString(Utils.BUILD_NAME));
            buildInfo.setFileSize(jsonObj.getLong(Utils.BUILD_SIZE));
            buildInfo.setMd5sum(jsonObj.getString(Utils.BUILD_MD5SUM));
            return buildInfo;
        }
        return null;
    }

    public void startDownload(File file, Network network, long startByte) {
        boolean append = startByte != 0;
        try {
            if (network != null) {
                dlConnection = (HttpsURLConnection) network.openConnection(downloadUrl);
            }
        } catch (IOException e) {
            Utils.log(e);
            return;
        }

        if (append) {
            dlConnection.setRequestProperty("Range", "bytes=" + startByte + "-");
        }

        try {
            rByteChannel = Channels.newChannel(dlConnection.getInputStream());
        } catch(IOException e) {
            Utils.log(e);
            return;
        }

        try {
            outStream = new FileOutputStream(file, append);
        } catch(FileNotFoundException e) {
            Utils.log(e);
            return;
        }

        fileChannel = outStream.getChannel();
        if (callback != null) {
            callback.onStartedDownload();
        }
        try {
            fileChannel.transferFrom(rByteChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            Utils.log(e);
            return;
        }
    }

    public void cleanup() {
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (outStream != null) {
                outStream.flush();
                outStream.close();
            }
            if (dlConnection != null) {
                dlConnection.disconnect();
            }
        } catch (IOException e) {
            // Do nothing
        }
    }

    public long getDownloadProgress() {
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                return fileChannel.size();
            }
        } catch(IOException e) {
            Utils.log(e);
        }
        return -1;
    }

    public boolean hasSetUrl() {
        return setUrl;
    }
}
