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
import static com.krypton.updater.util.Constants.CHANGELOG_UNAVAILABLE;
import static com.krypton.updater.util.Constants.CHANGELOG_UP_TO_DATE;
import static com.krypton.updater.util.Constants.FETCH_CHANGELOG_FAILED;
import static com.krypton.updater.util.Constants.FETCHING_CHANGELOG;
import static com.krypton.updater.util.Constants.GIT_BRANCH;
import static com.krypton.updater.util.Constants.NEW_CHANGELOG;
import static com.krypton.updater.util.Constants.NEW_UPDATE;
import static com.krypton.updater.util.Constants.OTA_JSON_FILE_NAME;
import static com.krypton.updater.util.Constants.REFRESH_FAILED;
import static com.krypton.updater.util.Constants.REFRESHING;
import static com.krypton.updater.util.Constants.UP_TO_DATE;

import android.util.Log;

import com.krypton.updater.util.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Singleton
public class GithubApiHelper {
    private static final String TAG = "GithubApiHelper";
    private static final String RAW_CONTENT_BASE_URL = "https://raw.githubusercontent.com/AOSP-Krypton/ota/";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com/repos/AOSP-Krypton/ota/git/trees/";
    private static final SimpleDateFormat CHANGELONG_FILE_DATE_FORMAT = new SimpleDateFormat("dd_MM_yy");
    private long date;

    @Inject
    public GithubApiHelper() {}

    public Response parseOTAInfo() {
        String json = Utils.parseRawContent(getContentURL(OTA_JSON_FILE_NAME));
        if (json != null) {
            try {
                final JSONObject jsonObj = new JSONObject(json);
                date = jsonObj.getLong(BUILD_DATE) * 1000; // Convert to millis here
                final String version = jsonObj.getString(BUILD_VERSION);
                final float currVersion = Float.parseFloat(Utils.getVersion());
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
                Log.e(TAG, "Exception when parsing json", e);
            }
        }
        return new Response(REFRESH_FAILED);
    }

    public Response parseChangelogInfo(final TreeMap<Date, Changelog> currentMap) {
        boolean listUpdated = false;
        String json = Utils.parseRawContent(getTreeURLForDevice());
        if (json != null) {
            try {
                final JSONObject jsonObj = new JSONObject(json);
                final JSONArray treeJSON = jsonObj.getJSONArray("tree");
                final int length = treeJSON.length();
                if (length == 1) {
                    // There is only an ota json present
                    return new Response(CHANGELOG_UNAVAILABLE);
                }
                final TreeMap<Date, Changelog> newMap = new TreeMap<>();
                for (int i = 0; i < length; i++) {
                    final JSONObject blobJSONObj = treeJSON.getJSONObject(i);
                    final String path = blobJSONObj.getString("path");
                    if (!path.equals(OTA_JSON_FILE_NAME)) {
                        final Changelog changelog = new Changelog();
                        Date changelogDate = new Date(date);
                        if (path.contains("changelog_")) {
                            changelogDate = getDateFromChangelogFile(
                                path.substring(path.indexOf("_") + 1));
                            if (changelogDate.getTime() < Utils.getBuildDate()) {
                                // Skip changelog older than current build
                                continue;
                            }
                        }
                        URL url = getContentURL(path);
                        if (url != null) {
                            final String sha = blobJSONObj.getString("sha");
                            if (!currentMap.containsKey(changelogDate) ||
                                    !currentMap.get(changelogDate).getSHA().equals(sha)) {
                                if (!listUpdated) {
                                    listUpdated = true;
                                }
                            }
                            newMap.put(changelogDate, new Changelog()
                                .setDate(changelogDate)
                                .setChangelog(Utils.parseRawContent(url))
                                .setSHA(sha));
                        }
                    }
                }
                return new Response(newMap, listUpdated ?
                    NEW_CHANGELOG : CHANGELOG_UP_TO_DATE);
            } catch(IllegalArgumentException|JSONException e) {
                Log.e(TAG, "Exception when parsing json", e);
            }
        }
        return new Response(FETCH_CHANGELOG_FAILED);
    }

    private static URL getTreeURLForDevice() {
        final String device = Utils.getDevice();
        try {
            URL treeURL = new URL(GITHUB_API_BASE_URL + GIT_BRANCH);
            final String rawJSON = Utils.parseRawContent(treeURL);
            if (rawJSON != null) {
                final JSONObject jsonObj = new JSONObject(rawJSON);
                final JSONArray treeJSON = jsonObj.getJSONArray("tree");
                for (int i = 0; i < treeJSON.length(); i++) {
                    JSONObject blobJSONObj = treeJSON.getJSONObject(i);
                    if (blobJSONObj.getString("path").equals(device)) {
                        return new URL(blobJSONObj.getString("url"));
                    }
                }
            }
        } catch(MalformedURLException|JSONException e) {
            Log.e(TAG, "Exception when parsing json", e);
        }
        return null;
    }

    private static URL getContentURL(String content) {
        try {
            return new URL(String.format("%s%s/%s/%s", RAW_CONTENT_BASE_URL,
                GIT_BRANCH, Utils.getDevice(), content));
        } catch(MalformedURLException e) {
            Log.e(TAG, "Malformed url", e);
        }
        return null;
    }

    public static Date getDateFromChangelogFile(String name) {
        try {
            return CHANGELONG_FILE_DATE_FORMAT.parse(name);
        } catch(ParseException e) {
            Log.e(TAG, "ParseException when parsing date " + name, e);
            return null;
        }
    }
}
