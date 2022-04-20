/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.data

import android.util.Log

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.krypton.updater.data.retrofit.Content
import com.krypton.updater.data.retrofit.OTAJsonContent
import com.krypton.updater.data.retrofit.GithubApiService

import javax.inject.Inject
import javax.inject.Singleton

import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient

import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.Retrofit

@Singleton
class GithubApiHelper @Inject constructor() {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().apply {
        if (DEBUG) addInterceptor(HttpLoggingInterceptor().also {
            it.level = HttpLoggingInterceptor.Level.BODY
        })
    }.build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
        .client(okHttpClient)
        .build()

    private val githubApiService = retrofit.create(GithubApiService::class.java)

    /**
     * Fetch OTA json file from github.
     * This method should not be called from main thread.
     *
     * @param device the device to fetch OTA json for.
     * @param incremental whether to fetch incremental update.
     * @return the OTA json file parsed as a [Result] of type [OTAJsonContent].
     *   (Maybe null if OTA info is not available).
     *   [Result] will represent a failure if an exception was thrown.
     */
    fun getBuildInfo(device: String, incremental: Boolean): Result<OTAJsonContent?> =
        runCatching {
            githubApiService
                .getOTAJsonContent(getUrlForDevice(device, incremental))
                .execute()
                .body()
        }

    /**
     * Fetch changelogs from github. This method should not be called
     * from main thread.
     *
     * @param device the device to fetch changelogs for.
     * @return the changelogs parsed as a [Result] with type as a [Map] of
     *   file name with it's content as a [String]. (Maybe empty id changelog
     *   is not available). [Result] will represent a failure if an exception
     *   was thrown.
     */
    fun getChangelogs(device: String): Result<Map<String, String?>?> =
        try {
            val contentList: List<Content>? = githubApiService
                .getContents(device, GIT_BRANCH)
                .execute()
                .body()
                ?.filterNot {
                    it.name == OTA_JSON || it.name == INCREMENTAL_OTA_JSON
                }
            when {
                contentList == null -> {
                    Result.success(null)
                }
                contentList.isEmpty() -> {
                    Result.success(emptyMap())
                }
                else -> {
                    val changelogMap = mutableMapOf<String, String?>()
                    contentList.forEach {
                        changelogMap[it.name] = githubApiService
                            .getChangelog(it.url)
                            .execute()
                            .body()
                    }
                    Result.success(changelogMap.toMap())
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        private const val TAG = "GithubApiHelper"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        // Git branch to fetch contents from
        private const val GIT_BRANCH = "A12"

        private const val OTA_JSON = "ota.json"
        private const val INCREMENTAL_OTA_JSON = "incremental_ota.json"

        private const val GITHUB_API_URL = "https://api.github.com/repos/AOSP-Krypton/ota/"
        private const val OTA_URL = "https://raw.githubusercontent.com/AOSP-Krypton/ota/"

        private fun getUrlForDevice(device: String, incremental: Boolean) =
            "$OTA_URL$GIT_BRANCH/$device/${if (incremental) INCREMENTAL_OTA_JSON else OTA_JSON}"
    }
}