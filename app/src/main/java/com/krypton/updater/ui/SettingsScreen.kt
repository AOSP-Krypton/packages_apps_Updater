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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController

import com.google.accompanist.systemuicontroller.SystemUiController
import com.krypton.updater.R
import com.krypton.updater.ui.preferences.DiscreteSeekBarPreference
import com.krypton.updater.ui.preferences.SwitchPreference
import com.krypton.updater.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    systemUiController: SystemUiController,
    navController: NavHostController,
) {
    val isSystemInDarkTheme = isSystemInDarkTheme()
    CollapsingToolbarScreen(
        title = stringResource(R.string.settings),
        backButtonContentDescription = stringResource(R.string.settings_back_button_content_desc),
        onBackButtonPressed = { navController.popBackStack() },
        onStatusBarColorUpdateRequest = {
            systemUiController.setStatusBarColor(
                color = it,
                darkIcons = !isSystemInDarkTheme
            )
        }
    ) {
        item {
            val updateCheckInterval = settingsViewModel.updateCheckInterval.collectAsState(0)
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
        }
        item {
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