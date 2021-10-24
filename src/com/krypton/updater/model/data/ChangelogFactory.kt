/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.model.data

import com.krypton.updater.model.room.ChangelogEntity

import java.util.Date

// TODO : remove the JvmStatic annotation once everything is in kotlin
object ChangelogFactory {
    @JvmStatic
    fun toChangelogEntity(changelogInfo: ChangelogInfo): ChangelogEntity {
        val changelogEntity = ChangelogEntity()
        changelogEntity.date = changelogInfo.date
        changelogEntity.changelog = changelogInfo.changelog
        changelogEntity.sha = changelogInfo.sha
        return changelogEntity
    }

    @JvmStatic
    fun toChangelogInfo(changelogEntity: ChangelogEntity) =
        ChangelogInfo(
            changelogEntity.date,
            changelogEntity.changelog,
            changelogEntity.sha,
        )
}