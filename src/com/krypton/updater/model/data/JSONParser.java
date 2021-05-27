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

import static com.krypton.updater.util.Constants.BUILD_DATE;
import static com.krypton.updater.util.Constants.BUILD_NAME;
import static com.krypton.updater.util.Constants.BUILD_SIZE;
import static com.krypton.updater.util.Constants.BUILD_MD5;
import static com.krypton.updater.util.Constants.BUILD_URL;
import static com.krypton.updater.util.Constants.BUILD_VERSION;
import static com.krypton.updater.util.Constants.NEW_UPDATE;
import static com.krypton.updater.util.Constants.REFRESH_FAILED;
import static com.krypton.updater.util.Constants.REFRESHING;
import static com.krypton.updater.util.Constants.UP_TO_DATE;

import com.krypton.updater.util.Utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.json.JSONException;
import org.json.JSONObject;

@Singleton
public class JSONParser {

    private static final String OTA_INFO_BASE_URL = "https://raw.githubusercontent.com/AOSP-Krypton/ota/A11/";

    @Inject
    public JSONParser() {}

    public Response parse() {
        final URL url = getContentURL();
        if (url != null) {
            String json = Utils.parseRawContent(url);
            if (json != null) {
                try {
                    final JSONObject jsonObj = new JSONObject(json);
                    long date = jsonObj.getLong(BUILD_DATE) * 1000; // Convert to millis here
                    String version = jsonObj.getString(BUILD_VERSION);
                    float currVersion = Float.parseFloat(Utils.getVersion());
                    if (Float.parseFloat(version) > currVersion || date > Utils.getBuildDate()) {
                        return new Response(new BuildInfo()
                            .setVersion(version)
                            .setDate(date)
                            .setFileName(jsonObj.getString(BUILD_NAME))
                            .setURL(jsonObj.getString(BUILD_URL))
                            .setMd5(jsonObj.getString(BUILD_MD5))
                            .setFileSize(jsonObj.getLong(BUILD_SIZE)), NEW_UPDATE);
                    } else {
                        return new Response(UP_TO_DATE);
                    }
                } catch(NumberFormatException|JSONException e) {
                    Utils.log(e);
                }
            }
        }
        return new Response(REFRESH_FAILED);
    }

    private URL getContentURL() {
        String device = Utils.getDevice();
        try {
            return new URL(String.format("%s%s/%s.json",
                OTA_INFO_BASE_URL, device, device));
        } catch(MalformedURLException e) {
            Utils.log(e);
        }
        return null;
    }
}
