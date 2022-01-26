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

import java.util.*

data class DownloadResult private constructor(
    private val exception: Throwable? = null,
    val isSuccess: Boolean = false,
    val isFailure: Boolean = false,
    val shouldRetry: Boolean = false,
) {
    fun exceptionOrNull(): Throwable? = exception

    override fun toString(): String =
        "DownloadResult[ isSuccess = $isSuccess, " +
                "isFailure = $isFailure, " +
                "shouldRetry = $shouldRetry, " +
                "exception = $exception ]"

    override fun equals(other: Any?): Boolean =
        other is DownloadResult &&
                isSuccess == other.isSuccess &&
                isFailure == other.isFailure &&
                shouldRetry == other.shouldRetry &&
                exception == other.exception

    override fun hashCode(): Int = Objects.hash(exception, isSuccess, isFailure, shouldRetry)

    companion object {
        fun success(): DownloadResult =
            DownloadResult(isSuccess = true)

        fun failure(exception: Throwable?): DownloadResult =
            DownloadResult(exception = exception, isFailure = true)

        fun retry(): DownloadResult =
            DownloadResult(shouldRetry = true)
    }
}