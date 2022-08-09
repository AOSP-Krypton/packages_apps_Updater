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

package com.flamingo.updater.data.retrofit

import androidx.annotation.Keep

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Keep
data class OTAJsonContent(
    @JsonProperty("version") val version: String,
    @JsonProperty("date") val date: Long,
    @JsonProperty("pre_build_incremental") val preBuildIncremental: String?,
    @JsonProperty("download_sources") val downloadSources: Map<String, String>,
    @JsonProperty("file_name") val fileName: String,
    @JsonProperty("file_size") val fileSize: Long,
    @JsonProperty("sha_512") val sha512: String,
)