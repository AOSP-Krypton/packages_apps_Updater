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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import org.json.JSONObject;

public class NetworkInterface {

    private boolean buildInfoUpdated = false;
    private boolean newBuildFound = false;

    private URL parseUrl() throws Exception {
        String device = Utils.getDevice();
        StringBuilder urlBuilder = new StringBuilder(Utils.BUILD_INFO_SOURCE_URL);
        urlBuilder.append(device + "/" + device + ".json");
        return new URL(urlBuilder.toString());
    }

    private void parseJSON(JSONObject obj) throws Exception {
        JSONObject buildInfo = obj.getJSONObject(Utils.BUILD_INFO);
        String version = buildInfo.getString(Utils.BUILD_VERSION);
        String timestamp = buildInfo.getString(Utils.BUILD_TIMESTAMP);
        String filename = buildInfo.getString(Utils.BUILD_NAME);
        newBuildFound = Utils.checkBuildStatus(version, timestamp);
        if (newBuildFound) {
            Utils.buildInfo = new Utils.BuildInfo(version, timestamp, filename);
        }
    }

    public void fetchBuildInfo() throws Exception {
        buildInfoUpdated = newBuildFound = false;
        InputStream rawStream = parseUrl().openStream();
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(rawStream));
        String tmp = buffReader.readLine();
        StringBuilder builder = new StringBuilder();
        while (tmp != null) {
            builder.append(tmp);
            tmp = buffReader.readLine();
        }
        rawStream.close();
        parseJSON(new JSONObject(builder.toString()));
        buildInfoUpdated = true;
    }

    public boolean hasUpdatedBuildInfo() {
        return buildInfoUpdated;
    }

    public boolean hasFoundNewBuild() {
        return newBuildFound;
    }
}
