/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.ui.states

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import com.flamingo.updater.data.MainRepository

import java.util.Date

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import org.koin.androidx.compose.get

class ChangelogScreenState(
    mainRepository: MainRepository
) {

    val changelog: Flow<List<Pair<Date, String?>>> = mainRepository.getUpdateInfo().map {
        val changelog = it.changelog ?: return@map emptyList()
        changelog.keys.sorted().map { date ->
            Pair(Date(date), changelog[date])
        }
    }
}

@Composable
fun rememberChangelogScreenState(
    mainRepository: MainRepository = get()
) = remember(mainRepository) {
    ChangelogScreenState(mainRepository = mainRepository)
}