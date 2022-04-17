/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.annotation.SuppressLint
import android.util.Log

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val githubApiHelper: GithubApiHelper,
) {

    private var updateBuildDate = 0L

    /**
     * Check for updates in github. This method should not
     * be called from main thread.
     *
     * @param incremental whether to fetch incremental update.
     * @return the fetch result as a [Result] of type [UpdateInfo].
     *   [UpdateInfo.Type] will indicate whether there is a new update or not.
     */
    fun checkForUpdate(incremental: Boolean): Result<UpdateInfo?> {
        var result = githubApiHelper.getBuildInfo(DeviceInfo.getDevice(), incremental)
        var isIncremental = incremental
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }
        if (result.getOrNull() == null) {
            // Fallback to full OTA if incremental is unavailable
            isIncremental = false
            result = githubApiHelper.getBuildInfo(DeviceInfo.getDevice(), false)
        }
        val otaJsonContent = result.getOrNull() ?: return Result.success(null)
        val buildInfo = BuildInfo(
            version = otaJsonContent.version,
            date = otaJsonContent.date,
            preBuildIncremental = otaJsonContent.preBuildIncremental,
            url = otaJsonContent.url,
            fileName = otaJsonContent.fileName,
            fileSize = otaJsonContent.fileSize,
            sha512 = otaJsonContent.sha512,
        )
        updateBuildDate = buildInfo.date
        val newUpdate = isNewUpdate(buildInfo, isIncremental)
        return Result.success(
            UpdateInfo(
                buildInfo = buildInfo,
                changelog = if (newUpdate) getChangelog() else null,
                type = if (newUpdate) UpdateInfo.Type.NEW_UPDATE else UpdateInfo.Type.NO_UPDATE
            )
        )
    }

    private fun getChangelog(): Map<Long, String?>? {
        val result = githubApiHelper.getChangelogs(DeviceInfo.getDevice())
        if (result.isFailure) {
            return null
        }
        val changelogMap = result.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val filteredMap = mutableMapOf<Long, String?>()
        changelogMap.forEach { (name, content) ->
            val date = getDateFromChangelogFileName(name)?.time ?: return@forEach
            if (compareTillDay(
                    date,
                    SYSTEM_BUILD_DATE
                ) < 0 /* Changelog is older than current build */
                || compareTillDay(
                    date,
                    updateBuildDate
                ) > 0 /* Changelog is newer than OTA */) {
                return@forEach
            }
            filteredMap[date] = content
        }
        return filteredMap.toMap()
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private val SYSTEM_BUILD_DATE: Long
            get() = DeviceInfo.getBuildDate()

        // Changelog files are of the format changelog_2021_12_30
        private const val CHANGELOG_FILE_NAME_PREFIX = "changelog_"

        @SuppressLint("SimpleDateFormat")
        private val CHANGELOG_FILE_DATE_FORMAT = SimpleDateFormat("yyyy_MM_dd")

        private fun getDateFromChangelogFileName(name: String): Date? =
            try {
                CHANGELOG_FILE_DATE_FORMAT.parse(
                    name.substringAfter(CHANGELOG_FILE_NAME_PREFIX)
                )
            } catch (e: ParseException) {
                Log.e(TAG, "ParseException while parsing date from $name")
                null
            }

        /**
         * Compares to unix timestamps by stripping of time until it
         * represents midnight of the day it corresponds to.
         */
        private fun compareTillDay(first: Long, second: Long): Int {
            if (DEBUG) Log.d(TAG, "Comparing: first = $first, second = $second")
            val calendarForFirst = setTimeAndResetTillDay(first)
            val calendarForSecond = setTimeAndResetTillDay(second)
            if (DEBUG) {
                Log.d(TAG, "calendarForFirst = $calendarForFirst")
                Log.d(TAG, "calendarForSecond = $calendarForSecond")
            }
            return calendarForFirst.compareTo(calendarForSecond)
        }

        private fun setTimeAndResetTillDay(time: Long) =
            Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        fun isNewUpdate(buildInfo: BuildInfo, incremental: Boolean): Boolean =
            if (incremental) {
                buildInfo.preBuildIncremental == DeviceInfo.getBuildVersionIncremental()
                        && (buildInfo.date) > SYSTEM_BUILD_DATE
            } else {
                (buildInfo.date) > SYSTEM_BUILD_DATE
            }
    }
}