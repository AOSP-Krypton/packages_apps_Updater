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

package com.krypton.updater.viewmodel

import androidx.lifecycle.ViewModel

import com.krypton.updater.data.MainRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import java.util.Date

import javax.inject.Inject

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val mainRepository: MainRepository,
) : ViewModel() {

    val changelog: Flow<List<Pair<Date, String?>>>
        get() = mainRepository.getUpdateInfo().map {
            val changelog = it.changelog ?: return@map emptyList()
            changelog.keys.sorted().map { date ->
                Pair(Date(date), changelog[date])
            }
        }
}