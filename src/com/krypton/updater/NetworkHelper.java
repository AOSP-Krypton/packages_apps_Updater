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

import android.net.Network;

import com.krypton.updater.BuildInfo;

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

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

public class NetworkHelper {

    private BuildInfo buildInfo;
    private URL downloadUrl;
    private boolean setUrl = false;
    private HttpsURLConnection dlConnection;
    private FileOutputStream outStream;
    private FileChannel fileChannel;
    private ReadableByteChannel rByteChannel;
    private Listener listener;

    public static interface Listener {
        public void onStartedDownload();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setDownloadUrl() {
        StringBuilder urlBuilder = new StringBuilder(Utils.DOWNLOAD_SOURCE_URL);
        urlBuilder.append(Utils.getDevice());
        urlBuilder.append('/');
        urlBuilder.append(buildInfo.getFileName());
        try {
            downloadUrl = new URL(urlBuilder.toString());
            setUrl = true;
        } catch(MalformedURLException e) {
            Utils.log(e);
        }
    }

    private String parseJSONUrl() {
        String device = Utils.getDevice();
        StringBuilder urlBuilder = new StringBuilder(Utils.BUILD_INFO_SOURCE_URL);
        urlBuilder.append(device + "/" + device + ".json");
        return urlBuilder.toString();
    }

    public BuildInfo fetchBuildInfo() throws IOException, JSONException {
        StringBuilder builder = new StringBuilder();
        InputStream rawStream;

        try {
            rawStream = new URL(parseJSONUrl()).openStream();
        } catch(MalformedURLException e) {
            return null;
        }

        BufferedReader buffReader = new BufferedReader(new InputStreamReader(rawStream));
        String tmp = buffReader.readLine();

        while (tmp != null) {
            builder.append(tmp);
            tmp = buffReader.readLine();
        }
        buffReader.close();
        rawStream.close();

        JSONObject jsonObj = new JSONObject(builder.toString()).getJSONObject(Utils.BUILD_INFO);
        String version = jsonObj.getString(Utils.BUILD_VERSION);
        String timestamp = jsonObj.getString(Utils.BUILD_TIMESTAMP);
        if (Utils.checkBuildStatus(version, timestamp)) {
            setUrl = false;
            buildInfo = new BuildInfo(version, timestamp,
                jsonObj.getString(Utils.BUILD_NAME),
                jsonObj.getLong(Utils.BUILD_SIZE),
                jsonObj.getString(Utils.BUILD_MD5SUM));
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
            return;
        }

        if (append) {
            dlConnection.setRequestProperty("Range", "bytes=" + startByte + "-");
        }

        try {
            rByteChannel = Channels.newChannel(dlConnection.getInputStream());
        } catch(IOException e) {
            return;
        }

        try {
            outStream = new FileOutputStream(file, append);
        } catch(FileNotFoundException e) {
            Utils.log(e);
            return;
        }

        fileChannel = outStream.getChannel();
        listener.onStartedDownload();
        try {
            fileChannel.transferFrom(rByteChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
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
            } else {
                return -1;
            }
        } catch(IOException e) {
            // Do nothing
        }
        return -1;
    }

    public boolean hasSetUrl() {
        return setUrl;
    }
}
