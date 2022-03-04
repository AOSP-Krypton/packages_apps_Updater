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

package com.krypton.updater.ui

import android.os.Bundle

import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference

import com.krypton.updater.R
import com.krypton.updater.viewmodel.SettingsViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    private val settingsViewModel: SettingsViewModel by activityViewModels()

    private var updateCheckIntervalPreference: SeekBarPreference? = null
    private var optOutIncrementalPreference: SwitchPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.fragment_settings)
        initUpdateCheckIntervalPreference()
        initOptOutIncrementalPreference()
    }

    private fun initUpdateCheckIntervalPreference() {
        updateCheckIntervalPreference = findPreference(KEY_UPDATE_CHECK_INTERVAL)
        settingsViewModel.updateCheckInterval.observe(this) {
            updateCheckIntervalPreference?.value = it
        }
        updateCheckIntervalPreference?.setOnPreferenceChangeListener { _, newValue ->
            settingsViewModel.setUpdateCheckInterval(newValue as Int)
            true
        }
    }

    private fun initOptOutIncrementalPreference() {
        optOutIncrementalPreference = findPreference(KEY_OPT_OUT_INCREMENTAL)
        settingsViewModel.optOutIncremental.observe(this) {
            optOutIncrementalPreference?.isChecked = it
        }
        optOutIncrementalPreference?.setOnPreferenceChangeListener { _, newValue ->
            settingsViewModel.setOptOutIncremental(newValue as Boolean)
            true
        }
    }

    companion object {
        private const val KEY_UPDATE_CHECK_INTERVAL = "update_check_interval_preference"
        private const val KEY_OPT_OUT_INCREMENTAL = "opt_out_incremental_preference"
    }
}