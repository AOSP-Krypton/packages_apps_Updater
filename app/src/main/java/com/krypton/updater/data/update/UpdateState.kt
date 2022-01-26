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

package com.krypton.updater.data.update

import java.util.Objects

class UpdateState private constructor(
    private val statusCode: Int,
    val exception: Throwable? = null,
) {
    val idle: Boolean
        get() = statusCode == IDLE
    val initializing: Boolean
        get() = statusCode == INITIALIZING
    val updating: Boolean
        get() = statusCode == UPDATING
    val paused: Boolean
        get() = statusCode == PAUSED
    val failed: Boolean
        get() = statusCode == FAILED
    val finished: Boolean
        get() = statusCode == FINISHED

    override fun toString(): String =
        "UpdateState[ idle = $idle, " +
                "initializing = $initializing, " +
                "updating = $updating, " +
                "paused = $paused, " +
                "failed = $failed, " +
                "finished = $finished," +
                "exception = $exception ]"

    override fun equals(other: Any?): Boolean =
        other is UpdateState &&
                statusCode == other.statusCode &&
                exception == other.exception

    override fun hashCode(): Int = Objects.hash(statusCode, exception)

    companion object {
        private const val IDLE = 0
        private const val INITIALIZING = 1
        private const val UPDATING = 2
        private const val PAUSED = 3
        private const val FAILED = 4
        private const val FINISHED = 5

        fun idle() = UpdateState(statusCode = IDLE)
        fun initializing() = UpdateState(statusCode = INITIALIZING)
        fun updating() = UpdateState(statusCode = UPDATING)
        fun paused() = UpdateState(statusCode = PAUSED)
        fun failure(e: Throwable?) = UpdateState(statusCode = FAILED, exception = e)
        fun finished() = UpdateState(statusCode = FINISHED)
    }
}