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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

import com.krypton.updater.R
import com.krypton.updater.ui.preferences.DiscreteSeekBarPreference
import com.krypton.updater.ui.preferences.SwitchPreference
import com.krypton.updater.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    navController: NavHostController,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = stringResource(R.string.settings)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            modifier = Modifier.padding(start = 12.dp),
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_button_content_desc)
                        )
                    }
                },
            )
        }
    ) {
        Column {
            val updateCheckInterval = settingsViewModel.updateCheckInterval.collectAsState(7)
            DiscreteSeekBarPreference(
                title = stringResource(R.string.update_check_interval_title),
                summary = stringResource(R.string.update_check_interval_summary),
                min = 1,
                max = 30,
                value = updateCheckInterval.value,
                showProgressText = true,
                onProgressChanged = {
                    settingsViewModel.setUpdateCheckInterval(it)
                },
            )
            val optOutIncremental = settingsViewModel.optOutIncremental.collectAsState(false)
            SwitchPreference(
                title = stringResource(R.string.opt_out_incremental_title),
                summary = stringResource(R.string.opt_out_incremental_summary),
                checked = optOutIncremental.value,
                onCheckedChange = {
                    settingsViewModel.setOptOutIncremental(it)
                }
            )
        }
    }
}