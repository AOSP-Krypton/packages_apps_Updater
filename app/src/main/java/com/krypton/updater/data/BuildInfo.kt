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

data class BuildInfo(
    val version: String,
    /**
     * UNIX timestamp in seconds
     */
    val date: Long,
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val sha512: String,
) {
    companion object {
        const val URL = "url"
        const val FILE_NAME = "file_name"
        const val FILE_SIZE = "file_size"
        const val SHA_512 = "sha_512"
    }
}