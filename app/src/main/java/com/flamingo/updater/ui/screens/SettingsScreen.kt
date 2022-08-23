/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.ui.screens

import android.content.Intent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController

import com.flamingo.support.compose.ui.layout.CollapsingToolbarLayout
import com.flamingo.support.compose.ui.preferences.DiscreteSeekBarPreference
import com.flamingo.support.compose.ui.preferences.Entry
import com.flamingo.support.compose.ui.preferences.ListPreference
import com.flamingo.support.compose.ui.preferences.SwitchPreference
import com.flamingo.updater.R
import com.flamingo.updater.data.settings.DEFAULT_AUTO_REBOOT
import com.flamingo.updater.data.settings.DEFAULT_AUTO_REBOOT_DELAY
import com.flamingo.updater.data.settings.DEFAULT_EXPORT_DOWNLOAD
import com.flamingo.updater.data.settings.DEFAULT_OPT_OUT_INCREMENTAL
import com.flamingo.updater.data.settings.DEFAULT_UPDATE_CHECK_INTERVAL
import com.flamingo.updater.ui.states.SettingsScreenState
import com.flamingo.updater.ui.states.rememberSettingsScreenState

@Composable
fun SettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    state: SettingsScreenState = rememberSettingsScreenState()
) {
    CollapsingToolbarLayout(
        modifier = modifier,
        title = stringResource(R.string.settings),
        onBackButtonPressed = { navController.popBackStack() }
    ) {
        item {
            val updateCheckIntervalState by state.updateCheckInterval.collectAsState(
                DEFAULT_UPDATE_CHECK_INTERVAL
            )
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
                    state.setUpdateCheckInterval(updateCheckInterval)
                }
            )
        }
        item {
            val optOutIncremental by state.optOutIncremental.collectAsState(
                DEFAULT_OPT_OUT_INCREMENTAL
            )
            SwitchPreference(
                title = stringResource(R.string.opt_out_incremental_title),
                summary = stringResource(R.string.opt_out_incremental_summary),
                checked = optOutIncremental,
                onCheckedChange = {
                    state.setOptOutIncremental(it)
                }
            )
        }
        item {
            val exportDownload by state.exportDownload.collectAsState(
                DEFAULT_EXPORT_DOWNLOAD
            )
            val contentResolver = LocalContext.current.contentResolver
            val launcher =
                rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) {
                    if (it == null) return@rememberLauncherForActivityResult
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    state.setExportDownload(true)
                }
            var showDialog by remember { mutableStateOf(false) }
            SwitchPreference(
                title = stringResource(R.string.export_downloads),
                summary = stringResource(R.string.export_downloads_summary),
                checked = exportDownload,
                onCheckedChange = { checked ->
                    if (checked) {
                        val hasPerms =
                            contentResolver.persistedUriPermissions.firstOrNull()?.takeIf {
                                it.isReadPermission && it.isWritePermission
                            } != null
                        if (!hasPerms) {
                            showDialog = true
                        }
                    } else {
                        state.setExportDownload(false)
                    }
                }
            )
            FileTreeOpenDialog(
                show = showDialog,
                onConfirmed = {
                    showDialog = false
                    launcher.launch(null)
                },
                onDismissRequest = {
                    showDialog = false
                },
            )
        }
        item {
            val autoReboot by state.autoReboot.collectAsState(initial = DEFAULT_AUTO_REBOOT)
            SwitchPreference(
                title = stringResource(id = R.string.auto_reboot),
                summary = stringResource(id = R.string.auto_reboot_summary),
                checked = autoReboot,
                onCheckedChange = {
                    state.setAutoReboot(it)
                }
            )
        }
        item {
            val autoRebootDelay by state.autoRebootDelay.collectAsState(initial = DEFAULT_AUTO_REBOOT_DELAY)
            ListPreference(
                title = stringResource(id = R.string.auto_reboot_delay),
                summary = stringResource(id = R.string.auto_reboot_delay_summary),
                entries = stringArrayResource(id = R.array.auto_reboot_delay_entries)
                    .zip(integerArrayResource(id = R.array.auto_reboot_delay_values).toTypedArray())
                    .map {
                        Entry(it.first, it.second.toLong())
                    },
                value = autoRebootDelay,
                onEntrySelected = {
                    state.setAutoRebootDelay(it)
                }
            )
        }
    }
}

@Composable
fun FileTreeOpenDialog(
    show: Boolean,
    onConfirmed: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = onConfirmed) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            title = {
                Text(text = stringResource(id = R.string.redirecting))
            },
            text = {
                Text(text = stringResource(id = R.string.confirm_to_open_file_picker))
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }
}