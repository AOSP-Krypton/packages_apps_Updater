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

package com.krypton.updater.data.download

class DownloadState private constructor(
    val idle: Boolean = false,
    val waiting: Boolean = false,
    val downloading: Boolean = false,
    val finished: Boolean = false,
    val failed: Boolean = false,
) {
    override fun toString(): String =
        "DownloadState[ idle = $idle, " +
                "waiting = $waiting, " +
                "downloading = $downloading, " +
                "finished = $finished, " +
                "failed = $failed ]"

    companion object {
        fun idle(): DownloadState =
            DownloadState(idle = true)

        fun waiting(): DownloadState =
            DownloadState(waiting = true)

        fun downloading(): DownloadState =
            DownloadState(downloading = true)

        fun finished(): DownloadState =
            DownloadState(finished = true)

        fun failed(): DownloadState =
            DownloadState(failed = true)
    }
}