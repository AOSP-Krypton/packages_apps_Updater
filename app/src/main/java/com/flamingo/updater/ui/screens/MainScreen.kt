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

package com.flamingo.updater.ui.screens

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

import com.flamingo.updater.R
import com.flamingo.updater.data.FileCopyStatus
import com.flamingo.updater.ui.states.*
import com.flamingo.updater.ui.widgets.AppBarMenu
import com.flamingo.updater.ui.widgets.CustomButton
import com.flamingo.updater.ui.widgets.MenuItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(state: MainScreenState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            val shouldAllowLocalUpgrade by state.shouldAllowUpdateCheckOrLocalUpgrade.collectAsState(
                false
            )
            val shouldAllowClearingCache by state.shouldAllowClearingCache.collectAsState(false)
            AppBar(
                shouldAllowLocalUpgrade,
                shouldAllowClearingCache,
                onRequestLocalUpgrade = {
                    state.startLocalUpgrade(it)
                },
                onSettingsLaunchRequest = {
                    state.openSettings()
                },
                onShowDownloadsRequest = {
                    state.openDownloads()
                },
                onClearCacheRequest = {
                    state.clearCache()
                }
            )
        },
        snackbarHost = {
            SnackbarHost(state.snackbarHostState)
        }
    ) { paddingValues ->
        val showStateRestoringDialog by state.showStateRestoreDialog.collectAsState(false)
        if (showStateRestoringDialog) {
            ProgressDialog(stringResource(id = R.string.restoring_state))
        }
        val otaFileCopyStatus by state.otaFileCopyStatus.collectAsState(null)
        FileCopyDialogAndSnackBar(
            otaFileCopyStatus,
            title = stringResource(id = R.string.copying_file),
            successMessage = stringResource(id = R.string.successfully_copied),
            failureMessage = R.string.copying_failed
        ) {
            state.showSnackBar(it)
        }
        val downloadFileExportStatus by state.downloadFileExportStatus.collectAsState(null)
        FileCopyDialogAndSnackBar(
            downloadFileExportStatus,
            title = stringResource(id = R.string.exporting_file),
            successMessage = stringResource(id = R.string.exported_file_successfully),
            failureMessage = R.string.exporting_failed
        ) {
            state.showSnackBar(it)
        }
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                UpdaterLogo(modifier = Modifier.fillMaxHeight(0.4f))
                Row(
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.systemBuildDate,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Divider(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                    )
                    Text(
                        text = stringResource(
                            id = R.string.system_version_format,
                            state.systemBuildVersion
                        ),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                val checkUpdatesContentState by state.checkUpdatesContentState.collectAsState(
                    CheckUpdatesContentState.Gone
                )
                AnimatedContent(
                    targetState = checkUpdatesContentState,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    val shouldAllowUpdateCheck by state.shouldAllowUpdateCheckOrLocalUpgrade.collectAsState(
                        false
                    )
                    CustomButton(
                        enabled = shouldAllowUpdateCheck && it !is CheckUpdatesContentState.Checking,
                        text = stringResource(id = R.string.check_for_updates),
                        onClick = {
                            state.checkForUpdates()
                        }
                    )
                }
                UpdateStatusContent(
                    modifier = Modifier.padding(top = 32.dp),
                    state = checkUpdatesContentState
                )
            }
            val cardState by state.cardState.collectAsState(CardState.Gone)
            AnimatedVisibility(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 32.dp, end = 12.dp, bottom = 12.dp),
                visible = cardState !is CardState.Gone,
                enter = slideInVertically { fullHeight ->
                    fullHeight
                } + fadeIn(),
                exit = slideOutVertically { fullHeight ->
                    fullHeight
                } + fadeOut()
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(32.dp),
                    content = {
                        when (cardState) {
                            is CardState.Update -> {
                                val updateCardState = rememberUpdateCardState(
                                    snackbarHostState = state.snackbarHostState
                                )
                                UpdateCard(updateCardState)
                            }
                            is CardState.Download -> {
                                val downloadCardState = rememberDownloadCardState(
                                    snackbarHostState = state.snackbarHostState,
                                    navHostController = state.navHostController
                                )
                                DownloadCard(downloadCardState)
                            }
                            is CardState.NoUpdate -> {
                                CardContent(
                                    title = stringResource(id = R.string.up_to_date),
                                    subtitle = stringResource(id = R.string.come_back_later)
                                )
                            }
                            is CardState.Gone -> {}
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppBar(
    shouldAllowLocalUpgrade: Boolean,
    shouldAllowClearingCache: Boolean,
    onRequestLocalUpgrade: (Uri) -> Unit,
    onSettingsLaunchRequest: () -> Unit,
    onShowDownloadsRequest: () -> Unit,
    onClearCacheRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localUpgradeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            if (it != null) onRequestLocalUpgrade(it)
        }
    )
    SmallTopAppBar(
        modifier = modifier,
        title = {},
        actions = {
            AppBarMenu(
                menuIcon = {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        stringResource(R.string.menu_button_content_desc)
                    )
                },
                menuItems = listOf(
                    MenuItem(
                        title = stringResource(id = R.string.local_upgrade),
                        icon = painterResource(id = R.drawable.ic_baseline_folder_24),
                        contentDescription = stringResource(id = R.string.local_upgrade_menu_item_desc),
                        enabled = shouldAllowLocalUpgrade,
                        onClick = {
                            localUpgradeLauncher.launch(arrayOf("application/zip"))
                        }
                    ),
                    MenuItem(
                        title = stringResource(id = R.string.downloads),
                        icon = painterResource(id = R.drawable.ic_baseline_cloud_download_24),
                        contentDescription = stringResource(id = R.string.downloads_menu_item_desc),
                        onClick = onShowDownloadsRequest
                    ),
                    MenuItem(
                        title = stringResource(id = R.string.clear_cache),
                        iconImageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(id = R.string.clear_cache_menu_item_desc),
                        enabled = shouldAllowClearingCache,
                        onClick = onClearCacheRequest
                    ),
                    MenuItem(
                        title = stringResource(id = R.string.settings),
                        iconImageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(id = R.string.settings_menu_item_desc),
                        onClick = onSettingsLaunchRequest
                    ),
                )
            )
        }
    )
}

@Composable
fun ProgressDialog(title: String, modifier: Modifier = Modifier) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        confirmButton = {},
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        shape = RoundedCornerShape(32.dp),
        text = {
            LinearProgressIndicator()
        },
    )
}

@Composable
fun FileCopyDialogAndSnackBar(
    status: FileCopyStatus?,
    title: String,
    successMessage: String,
    failureMessage: Int,
    onShowSnackBarRequest: (String) -> Unit
) {
    when (status) {
        is FileCopyStatus.Copying -> {
            ProgressDialog(title)
        }
        is FileCopyStatus.Success -> {
            onShowSnackBarRequest(successMessage)
        }
        is FileCopyStatus.Failure -> {
            onShowSnackBarRequest(
                stringResource(
                    failureMessage,
                    status.reason ?: ""
                )
            )
        }
        else -> {}
    }
}

@Composable
fun UpdaterLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(6.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(0.8f),
            painter = painterResource(id = R.drawable.ic_updater_logo),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.updater_logo_content_desc)
        )
    }
}

@Composable
@Preview
fun PreviewUpdaterLogo() {
    UpdaterLogo()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UpdateStatusContent(state: CheckUpdatesContentState, modifier: Modifier = Modifier) {
    AnimatedContent(modifier = modifier, targetState = state) {
        when (it) {
            is CheckUpdatesContentState.Gone -> {}
            is CheckUpdatesContentState.Checking -> {
                Text(text = stringResource(id = R.string.checking_for_update))
            }
            is CheckUpdatesContentState.LastCheckedTimeAvailable -> {
                Text(
                    text = stringResource(
                        id = R.string.last_checked_time_format,
                        it.lastCheckedTime
                    )
                )
            }
        }
    }
}