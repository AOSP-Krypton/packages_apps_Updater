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

package com.flamingo.updater.data.room

import androidx.room.TypeConverter

import org.json.JSONException
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromString(value: String?): Map<String, String>? {
        if (value == null) return null
        return try {
            val jsonObject = JSONObject(value)
            val map = mutableMapOf<String, String>()
            jsonObject.keys().forEach {
                map[it] = jsonObject.getString(it)
            }
            map.toMap()
        } catch (e: JSONException) {
            null
        }
    }

    @TypeConverter
    fun mapToString(value: Map<String, String>?): String? {
        if (value == null) return null
        return try {
            val jsonObject = JSONObject()
            value.forEach {
                jsonObject.put(it.key, it.value)
            }
            jsonObject.toString()
        } catch (e: JSONException) {
            null
        }
    }
}