/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
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

package com.kosp.updater.ui.states

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import com.kosp.updater.data.MainRepository
import com.kosp.updater.data.UpdateInfo

import java.util.Date

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

import org.koin.androidx.compose.get

class ChangelogScreenState(
    mainRepository: MainRepository
) {
    val changelog: Flow<List<Pair<Date, String?>>> = mainRepository.updateInfo
        .filterIsInstance<UpdateInfo.NewUpdate>()
        .map { it.changelog }
        .filterNotNull()
        .map { changelogMap ->
            changelogMap.mapKeys { Date(it.key) }.toSortedMap().toList()
        }
}

@Composable
fun rememberChangelogScreenState(
    mainRepository: MainRepository = get()
) = remember(mainRepository) {
    ChangelogScreenState(mainRepository = mainRepository)
}