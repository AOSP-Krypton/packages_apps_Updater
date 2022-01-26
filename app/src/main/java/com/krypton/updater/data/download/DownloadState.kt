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

import java.util.Objects

class DownloadState private constructor(
    private val statusCode: Int,
    val exception: Throwable? = null,
) {
    val idle: Boolean
        get() = statusCode == IDLE
    val waiting: Boolean
        get() = statusCode == WAITING
    val downloading: Boolean
        get() = statusCode == DOWNLOADING
    val finished: Boolean
        get() = statusCode == FINISHED
    val failed: Boolean
        get() = statusCode == FAILED

    override fun toString(): String =
        "DownloadState[ idle = $idle, " +
                "waiting = $waiting, " +
                "downloading = $downloading, " +
                "finished = $finished, " +
                "failed = $failed, " +
                "exception = $exception ]"

    override fun equals(other: Any?): Boolean =
        other is DownloadState &&
                statusCode == other.statusCode &&
                exception == other.exception

    override fun hashCode(): Int = Objects.hash(statusCode, exception)

    companion object {
        private const val IDLE = 0
        private const val WAITING = 1
        private const val DOWNLOADING = 2
        private const val FINISHED = 3
        private const val FAILED = 4

        fun idle() = DownloadState(statusCode = IDLE)
        fun waiting() = DownloadState(statusCode = WAITING)
        fun downloading() = DownloadState(statusCode = DOWNLOADING)
        fun failed(e: Throwable?) = DownloadState(statusCode = FAILED, exception = e)
        fun finished() = DownloadState(statusCode = FINISHED)
    }
}