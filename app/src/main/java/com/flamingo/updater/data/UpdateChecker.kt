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

import java.util.Calendar
import java.util.Date

class UpdateChecker(
    private val context: Context,
    private val githubApiHelper: GithubApiHelper,
) {

    /**
     * Check for updates in github. This method should not
     * be called from main thread.
     *
     * @param incremental whether to fetch incremental update.
     * @return the fetch result as [UpdateInfo].
     */
    fun checkForUpdate(incremental: Boolean): UpdateInfo {
        val result = githubApiHelper.getBuildInfo(Device, BuildFlavor, incremental)
        logD("GET result = $result")
        if (result.isFailure) {
            Log.e(TAG, "Primary update check failed", result.exceptionOrNull())
            return if (incremental) {
                // Fallback to full OTA hoping that it may work
                checkForUpdate(false)
            } else {
                Log.e(TAG, "Update check failed", result.exceptionOrNull())
                UpdateInfo.Error(
                    result.exceptionOrNull()
                        ?: Throwable(context.getString(R.string.update_check_failed))
                )
            }
        }
        val otaJsonContent = result.getOrNull() ?: run {
            return if (incremental) {
                logD("Incremental ota json unavailable, checking full ota")
                checkForUpdate(false)
            } else {
                UpdateInfo.Unavailable
            }
        }
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
        return if (isNewUpdate(buildInfo, incremental)) {
            UpdateInfo.NewUpdate(
                buildInfo = buildInfo,
                changelog = getChangelog(buildInfo.date)
            )
        } else {
            if (incremental) {
                logD("New incremental update not found, checking full ota")
                checkForUpdate(false)
            } else {
                UpdateInfo.NoUpdate
            }
        }
    }

    private fun getChangelog(updateBuildDate: Long): Map<Long, String?>? {
        val result = githubApiHelper.getChangelogs(Device, BuildFlavor)
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
                    BuildDate
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
            (buildInfo.date) > BuildDate &&
                    (!incremental || buildInfo.preBuildIncremental == BuildVersionIncremental)
    }
}

sealed interface UpdateInfo {
    object NoUpdate : UpdateInfo
    object Unavailable : UpdateInfo

    data class Error(val exception: Throwable) : UpdateInfo

    data class NewUpdate(
        val buildInfo: BuildInfo,
        val changelog: Map<Long, String?>?
    ) : UpdateInfo
}