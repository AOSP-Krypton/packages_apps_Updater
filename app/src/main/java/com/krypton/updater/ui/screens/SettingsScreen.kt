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

package com.krypton.updater.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.google.accompanist.systemuicontroller.SystemUiController
import com.krypton.updater.R
import com.krypton.updater.ui.preferences.DiscreteSeekBarPreference
import com.krypton.updater.ui.preferences.SwitchPreference
import com.krypton.updater.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
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
            val updateCheckIntervalState by settingsViewModel.updateCheckInterval.collectAsState(0)
            var updateCheckInterval by remember(updateCheckIntervalState) {
                mutableStateOf(
                    updateCheckIntervalState
                )
            }
            DiscreteSeekBarPreference(
                title = stringResource(R.string.update_check_interval_title),
                summary = stringResource(R.string.update_check_interval_summary),
                min = 1,
                max = 30,
                value = updateCheckInterval,
                showProgressText = true,
                onProgressChanged = {
                    updateCheckInterval = it
                },
                onProgressChangeFinished = {
                    settingsViewModel.setUpdateCheckInterval(updateCheckInterval)
                }
            )
        }
        item {
            val optOutIncremental by settingsViewModel.optOutIncremental.collectAsState(false)
            SwitchPreference(
                title = stringResource(R.string.opt_out_incremental_title),
                summary = stringResource(R.string.opt_out_incremental_summary),
                checked = optOutIncremental,
                onCheckedChange = {
                    settingsViewModel.setOptOutIncremental(it)
                }
            )
        }
    }
}