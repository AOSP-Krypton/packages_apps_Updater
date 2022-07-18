/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.data

import android.content.Context
import android.util.Log

import com.flamingo.updater.R

import dagger.hilt.android.qualifiers.ApplicationContext

import java.util.Calendar
import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
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
        val result = githubApiHelper.getBuildInfo(
            DeviceInfo.getDevice(),
            DeviceInfo.getFlavor(),
            incremental
        )
        if (result.isFailure) {
            return if (incremental) {
                // Fallback to full OTA hoping that it may work
                checkForUpdate(false)
            } else {
                Log.e(TAG, "Update check failed", result.exceptionOrNull())
                Result.failure(
                    result.exceptionOrNull()
                        ?: Throwable(context.getString(R.string.update_check_failed))
                )
            }
        }
        val otaJsonContent = result.getOrNull() ?: return Result.success(null)
        logD("incremental = $incremental, otaJsonContent = $otaJsonContent")
        val buildInfo = BuildInfo(
            version = otaJsonContent.version,
            date = otaJsonContent.date,
            preBuildIncremental = otaJsonContent.preBuildIncremental,
            downloadSources = otaJsonContent.downloadSources,
            fileName = otaJsonContent.fileName,
            fileSize = otaJsonContent.fileSize,
            sha512 = otaJsonContent.sha512,
        )
        updateBuildDate = buildInfo.date
        val newUpdate = isNewUpdate(buildInfo, incremental)
        return if (!newUpdate && incremental) {
            checkForUpdate(false)
        } else {
            Result.success(
                UpdateInfo(
                    buildInfo = buildInfo,
                    changelog = if (newUpdate) getChangelog() else null,
                    type = if (newUpdate) UpdateInfo.Type.NEW_UPDATE else UpdateInfo.Type.NO_UPDATE
                )
            )
        }
    }

    private fun getChangelog(): Map<Long, String?>? {
        val result = githubApiHelper.getChangelogs(DeviceInfo.getDevice())
        logD("getChangelog: result = $result")
        if (result.isFailure) {
            Log.e(TAG, "Failed to get changelog", result.exceptionOrNull())
            return null
        }
        val changelogMap = result.getOrNull()?.takeIf { it.isNotEmpty() } ?: run {
            Log.w(TAG, "Empty changelog!")
            return null
        }
        val filteredMap = mutableMapOf<Long, String?>()
        changelogMap.forEach { (name, content) ->
            logD("filtering $name")
            val date = getDateFromChangelogFileName(name)?.time ?: return@forEach
            if (compareTillDay(
                    date,
                    SYSTEM_BUILD_DATE
                ) < 0 /* Changelog is older than current build */
                || compareTillDay(
                    date,
                    updateBuildDate
                ) > 0 /* Changelog is newer than OTA */) {
                logD("Skipping changelog since it doesn't satisfy constraints")
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

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }

        private fun getDateFromChangelogFileName(name: String): Date? {
            return runCatching {
                val dateStringList =
                    name.substringAfter(CHANGELOG_FILE_NAME_PREFIX).split("_", limit = 3)
                logD("parsed dates = $dateStringList")
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, dateStringList[0].toInt())
                    set(
                        Calendar.MONTH,
                        dateStringList[1].toInt() - 1 /* 0 is first month in gregorian calendar */
                    )
                    set(Calendar.DAY_OF_MONTH, dateStringList[2].toInt())
                }
                calendar.time
            }.getOrNull()
        }

        /**
         * Compares two unix timestamps by stripping of time until it
         * represents midnight of the day it corresponds to.
         */
        private fun compareTillDay(first: Long, second: Long): Int {
            logD("Comparing: first = $first, second = $second")
            val calendarForFirst = setTimeAndResetTillDay(first)
            val calendarForSecond = setTimeAndResetTillDay(second)
            logD("processed times: first = ${calendarForFirst.time.time}, second = ${calendarForSecond.time.time}")
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