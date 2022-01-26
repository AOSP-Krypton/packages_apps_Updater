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

import java.util.Objects

class Event<out T>(
    private val data: T,
) {
    var hasBeenHandled = false
        private set

    fun getOrNull(): T? =
        if (hasBeenHandled) null
        else {
            hasBeenHandled = true
            data
        }

    fun peek(): T = data

    override fun toString(): String =
        "Event[ data = $data, " +
                "hasBeenHandled = $hasBeenHandled]"

    override fun equals(other: Any?): Boolean =
        other is Event<*> &&
                data == other.data &&
                hasBeenHandled == other.hasBeenHandled

    override fun hashCode(): Int = Objects.hash(data, hasBeenHandled)
}