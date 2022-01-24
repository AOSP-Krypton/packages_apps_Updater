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

    private val systemBuildDate = DeviceInfo.getBuildDate()
    private var updateBuildDate = 0L

    /**
     * Check for updates in github. This method should not
     * be called from main thread.
     *
     * @return the fetch result as a [Result] of type [UpdateInfo].
     *   [UpdateInfo.Type] will indicate whether there is a new update or not.
     */
    fun checkForUpdate(): Result<UpdateInfo> {
        val result = githubApiHelper.getBuildInfo(DeviceInfo.getDevice())
        return if (result.isSuccess) {
            val otaJsonContent = result.getOrThrow()
            val buildInfo = BuildInfo(
                version = otaJsonContent.version,
                date = otaJsonContent.date,
                url = otaJsonContent.url,
                fileName = otaJsonContent.fileName,
                fileSize = otaJsonContent.fileSize,
                sha512 = otaJsonContent.sha512,
            )
            updateBuildDate = buildInfo.date * 1000 /* convert to millis */
            val newUpdate = updateBuildDate > systemBuildDate
            Result.success(
                UpdateInfo(
                    buildInfo = buildInfo.copy(date = updateBuildDate),
                    changelog = if (newUpdate) getChangelog() else null,
                    type = if (newUpdate) UpdateInfo.Type.NEW_UPDATE else UpdateInfo.Type.NO_UPDATE
                )
            )
        } else {
            Result.failure(result.exceptionOrNull()!!)
        }
    }

    private fun getChangelog(): Map<Long, String?>? {
        val result = githubApiHelper.getChangelogs(DeviceInfo.getDevice())
        if (result.isSuccess) {
            val changelogMap = result.getOrThrow()
            if (changelogMap.isEmpty()) return null
            val filteredMap = mutableMapOf<Long, String?>()
            changelogMap.forEach { (name, content) ->
                val date = getDateFromChangelogFileName(name) ?: return@forEach
                if (compareTillDay(date, systemBuildDate) < 0 /* Changelog is older than current build */
                        || compareTillDay(date, updateBuildDate) > 0 /* Changelog is newer than OTA (wtf?) */) {
                    return@forEach
                }
                filteredMap[date.time] = content
            }
            return filteredMap.toMap()
        } else return null
    }

    companion object {
        private const val TAG = "UpdateChecker"

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

        private fun compareTillDay(first: Date, second: Long): Int {
            val firstCalendar = Calendar.getInstance().apply { time = first }

            val secondCalendar = Calendar.getInstance().apply {
                timeInMillis = second
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return firstCalendar.compareTo(secondCalendar)
        }
    }
}