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

package com.krypton.updater.ui

import android.os.Bundle
import android.widget.TextView

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

import com.krypton.updater.R
import com.krypton.updater.viewmodel.ChangelogViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChangelogActivity: AppCompatActivity() {
    private val viewModel: ChangelogViewModel by viewModels()
    private lateinit var changelogTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        changelogTextView = findViewById(R.id.changelog_text)
    }

    override fun onStart() {
        super.onStart()
        viewModel.changelog.observe(this) {
            if (it == null) {
                changelogTextView.setText(R.string.changelog_unavailable)
            } else {
                changelogTextView.setText(it, TextView.BufferType.SPANNABLE)
            }
        }
    }
}