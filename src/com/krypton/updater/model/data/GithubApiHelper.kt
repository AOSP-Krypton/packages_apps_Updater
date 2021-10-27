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

package com.krypton.updater.model.data

import android.util.Log

import androidx.annotation.WorkerThread

import com.krypton.updater.model.retrofit.data.Content
import com.krypton.updater.model.retrofit.data.OTAJsonContent
import com.krypton.updater.model.retrofit.GithubApiService

import java.io.IOException
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton

import kotlin.math.sign

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.Retrofit

@Singleton
class GithubApiHelper @Inject constructor() {

    private val loggingInterceptor = HttpLoggingInterceptor()
    private val okHttpClient: OkHttpClient
    private val retrofit: Retrofit
    private val githubApiService: GithubApiService

    private var fetchedBuildDate: Long = 0

    init {
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        okHttpClient = OkHttpClient.Builder().apply {
            if (DEBUG) addInterceptor(loggingInterceptor)
        }.build()
        retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/repos/AOSP-Krypton/ota/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
        githubApiService = retrofit.create(GithubApiService::class.java)
    }

    /*
     * Fetch ota.json file from github for the given
     * @param device. Returns a BuildInfo object on success
     */
    @WorkerThread
    fun getBuildInfo(device: String): BuildInfo? {
        logD("getBuildInfo, device = $device")
        val urlString = getUrlForDevice(device)
        logD("getBuildInfo, url = $urlString")
        try {
            val otaJsonContent: OTAJsonContent? = githubApiService
                .getOTAJsonContent(urlString)
                .execute()
                .body()
            logD("otaJsonContent = $otaJsonContent")
            return otaJsonContent?.let {
                fetchedBuildDate = it.date
                return BuildInfo(
                    it.version,
                    it.date,
                    it.url,
                    it.fileName,
                    it.fileSize,
                    it.md5,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while parsing ota info", e)
        }
        return null
    }

    /*
     * Fetch all the changelog_YYYY_MM_DD files for the given
     * @param device, filtered based on @param dateLowerBound (in millis).
     * (Date of changelog must be greater than or equal to dateLowerBound)
     * Returns a List<ChangelogInfo> on success
     */
    @WorkerThread
    fun getChangelogs(
        device: String,
        dateLowerBound: Long,
    ): List<ChangelogInfo>? {
        logD("getChangelogs, device = $device, from = $dateLowerBound")
        try {
            val contentList: List<Content>? = githubApiService
                .getContents(device, GIT_BRANCH)
                .execute()
                .body()
                ?.filter { it.name != OTA_JSON_FILE_NAME }
            logD("getChangelogs, contentList = $contentList")
            if (contentList == null) {
                return null
            } else if (contentList.size == 0) {
                return listOf<ChangelogInfo>()
            }
            val mutableChangelogList = mutableListOf<ChangelogInfo>()
            contentList.forEach { content ->
                var changelogDate: Date? = getDateFromChangelogFileName(content.name)
                logD("getChangelogs, changelogDate = $changelogDate, fetchedBuildDate = $fetchedBuildDate")
                // Skip changelog for builds older than the current build
                // or newer than the ota (this shouldn't happen but it's not impossible)
                if (changelogDate == null ||
                        compareTillDay(changelogDate, dateLowerBound) < 0 ||
                        compareTillDay(changelogDate, fetchedBuildDate) > 0) {
                    logD("getChangelogs, skipping older changelog")
                    return@forEach
                }
                val changelogString: String? = githubApiService
                    .getChangelog(content.url)
                    .execute()
                    .body()
                mutableChangelogList.add(
                    ChangelogInfo(
                        changelogDate,
                        changelogString,
                        content.sha,
                    ),
                )
            }
            return mutableChangelogList.toList()
        } catch(e: Exception) {
            Log.e(TAG, "Exception when parsing changelog info", e)
        }
        return null
    }

    companion object {
        private const val TAG = "GithubApiHelper"
        private const val DEBUG = false

        // Git branch to fetch contents from
        private const val GIT_BRANCH = "A12-test"

        // File name of the ota json
        private const val OTA_JSON_FILE_NAME = "ota.json"

        private const val RAW_CONTENT_BASE_URL =
            "https://raw.githubusercontent.com/AOSP-Krypton/ota/$GIT_BRANCH"

        // Changelog files are of the format changelog_2021_12_30
        private const val CHANGELOG_FILE_NAME_PREFIX = "changelog_"
        private val CHANGELONG_FILE_DATE_FORMAT = SimpleDateFormat("yyyy_MM_dd")

        private fun getUrlForDevice(device: String) = "$RAW_CONTENT_BASE_URL/$device/$OTA_JSON_FILE_NAME"

        private fun getDateFromChangelogFileName(name: String): Date? =
            try {
                CHANGELONG_FILE_DATE_FORMAT.parse(
                    name.substringAfter(CHANGELOG_FILE_NAME_PREFIX))
            } catch(e: ParseException) {
                Log.e(TAG, "ParseException while parsing date from $name")
                null
            }

        /*
         * Compares to date without considering it's time after a day's start,
         * i.e. milliseconds since 0:00 is not considered
         */
        //private fun compareTillDay(first: Long, second: Long): Int =
          //  ((first / DAY_IN_MILLIS) - (second / DAY_IN_MILLIS)).sign

        private fun compareTillDay(first: Date, second: Long): Int {
            logD("compareTillDay, $first, $second")

            val firstCalendar = Calendar.getInstance().also { it.setTime(first) }

            val secondCalendar = Calendar.getInstance().apply {
                setTimeInMillis(second)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return firstCalendar.compareTo(secondCalendar)
        }

        private fun logD(msg: String?) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
