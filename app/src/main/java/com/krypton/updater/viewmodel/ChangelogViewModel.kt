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

package com.krypton.updater.viewmodel

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.MainRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.Inject

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class ChangelogViewModel @Inject constructor(
    mainRepository: MainRepository,
) : ViewModel() {
    private val _changelog = MutableLiveData<SpannableStringBuilder?>(null)
    val changelog: LiveData<SpannableStringBuilder?>
        get() = _changelog

    init {
        viewModelScope.launch {
            mainRepository.updateInfo.filterNotNull().collect {
                if (it.isSuccess) {
                    _changelog.value = joinChangelog(it.getOrThrow().changelog)
                } else {
                    _changelog.value = null
                }
            }
        }
    }

    private fun joinChangelog(changelogs: Map<Long, String?>?) =
        SpannableStringBuilder().apply {
            changelogs?.forEach { (date, changelog) ->
                val startIndex = length
                append(formatDate(date))
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    startIndex,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append("\n\n$changelog\n")
            }
        }

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy")

        private fun formatDate(time: Long) = DATE_FORMAT.format(Date(time))
    }
}