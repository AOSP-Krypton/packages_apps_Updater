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

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore

import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    private const val UPDATE_CHECK_INTERVAL_DEFAULT = 7

    override val defaultValue: Settings = Settings.newBuilder()
        .setUpdateCheckInterval(UPDATE_CHECK_INTERVAL_DEFAULT)
        .build()

    override suspend fun readFrom(input: InputStream): Settings {
        val readResult = runCatching {
            Settings.parseFrom(input)
        }
        if (readResult.isSuccess) {
            return readResult.getOrThrow()
        }
        throw readResult.exceptionOrNull() ?: Throwable("Failed to read from input stream")
    }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream
    ) {
        val writeResult = runCatching {
            t.writeTo(output)
        }
        if (writeResult.isSuccess) {
            return writeResult.getOrThrow()
        }
        throw writeResult.exceptionOrNull() ?: Throwable("Failed to write to output stream")
    }
}

val Context.appSettings: DataStore<Settings> by dataStore(
    fileName = "settings.proto",
    serializer = SettingsSerializer
)