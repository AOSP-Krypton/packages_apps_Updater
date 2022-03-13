/*
 * Copyright (C) 2022 AOSP-Krypton Project
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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

import com.krypton.updater.R
import com.krypton.updater.services.UpdateInstallerService
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel
import com.krypton.updater.viewmodel.UpdateViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var bound = false
    private var updateInstallerService: UpdateInstallerService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logD("onServiceConnected")
            updateInstallerService = (service as? UpdateInstallerService.ServiceBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logD("onServiceDisconnected")
            updateInstallerService = null
        }
    }

    private val documentTreeContract =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it == null) {
                finish()
                return@registerForActivityResult
            }
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, flags)
        }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        checkExportFolderPermission()
        logD("binding service")
        bound = bindService(
            Intent(this, UpdateInstallerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        logD("bound = $bound")
        setContent {
            AppTheme {
                val mainScreenState = rememberMainScreenState(
                    viewModels<MainViewModel>().value,
                    viewModels<DownloadViewModel>().value,
                    viewModels<UpdateViewModel>().value
                )
                MainScreen(mainScreenState)
            }
        }
    }

    private fun checkExportFolderPermission() {
        val hasPerms = contentResolver.persistedUriPermissions.firstOrNull()?.let {
            it.isReadPermission && it.isWritePermission
        } ?: false
        if (!hasPerms) {
            documentTreeContract.launch(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(state: MainScreenState) {
        Scaffold(
            topBar = {
                AppBar(onRequestLocalUpgrade = {
                    state.startLocalUpgrade(it)
                })
            },
            snackbarHost = { SnackbarHost(state.snackbarHostState) }
        ) {
            val showExportDialog = state.showExportingDialog.collectAsState(false)
            if (showExportDialog.value) {
                ProgressDialog(title = stringResource(id = R.string.exporting_file))
            }
            val showCopyingDialog = state.showCopyingDialog.collectAsState(false)
            if (showCopyingDialog.value) {
                ProgressDialog(title = stringResource(id = R.string.copying_file))
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight(TOP_CONTENT_HEIGHT_FRACTION),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    MainScreenTopContent(
                        state.systemBuildDate,
                        state.systemBuildVersion,
                        state.isCheckingForUpdate.collectAsState(false),
                        state.lastCheckedTime.collectAsState(null),
                        onUpdateCheckRequest = {
                            state.checkForUpdates()
                        }
                    )
                }
                val showCardState = state.shouldShowCard.collectAsState(initial = false)
                val visible by remember { showCardState }
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = visible,
                    enter = slideInVertically { fullHeight ->
                        fullHeight
                    } + fadeIn(),
                    exit = slideOutVertically { fullHeight ->
                        fullHeight
                    } + fadeOut()
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(1 - TOP_CONTENT_HEIGHT_FRACTION)
                            .padding(start = 12.dp, top = 32.dp, end = 12.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(32.dp),
                        content = {
                            val showUpdateUI = state.showUpdateUI.collectAsState(false)
                            val showDownloadUI =
                                state.showDownloadUI.collectAsState(false)
                            when {
                                // Order matters, update ui takes preference over download and so on
                                showUpdateUI.value -> {
                                    val updateCardState = rememberUpdateCardState(
                                        viewModels<UpdateViewModel>().value,
                                        updateInstallerService,
                                        state.snackbarHostState,
                                        rememberCoroutineScope()
                                    )
                                    UpdateCard(updateCardState)
                                }
                                showDownloadUI.value -> {
                                    val downloadCardState = rememberDownloadCardState(
                                        viewModels<MainViewModel>().value,
                                        viewModels<DownloadViewModel>().value,
                                        state.snackbarHostState,
                                        rememberCoroutineScope()
                                    )
                                    DownloadCard(downloadCardState)
                                }
                                else -> NoUpdateCard()
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun AppBar(onRequestLocalUpgrade: (Uri) -> Unit) {
        val localUpgradeLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = {
                if (it != null) onRequestLocalUpgrade(it)
            }
        )
        SmallTopAppBar(
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
                            onClick = {
                                localUpgradeLauncher.launch(ZIP_MIME)
                            }
                        ),
                        MenuItem(
                            title = stringResource(id = R.string.settings),
                            iconImageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(id = R.string.settings_menu_item_desc),
                            onClick = {
                                startActivity(
                                    Intent(
                                        applicationContext,
                                        SettingsActivity::class.java
                                    )
                                )
                            }
                        ),
                    )
                )
            }
        )
    }

    @OptIn(ExperimentalUnitApi::class)
    @Composable
    fun ProgressDialog(title: String) {
        AlertDialog(
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
    fun UpdaterLogo() {
        Box(modifier = Modifier.size(200.dp)) {
            Icon(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                painter = painterResource(id = R.drawable.ic_updater_logo_part_0),
                tint = MaterialTheme.colorScheme.surfaceVariant,
                contentDescription = stringResource(R.string.updater_logo_content_desc)
            )
            Icon(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .padding(top = 1.dp),
                painter = painterResource(id = R.drawable.ic_updater_logo_part_1),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = stringResource(R.string.updater_logo_content_desc)
            )
            Icon(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                painter = painterResource(id = R.drawable.ic_updater_logo_part_2),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = stringResource(R.string.updater_logo_content_desc)
            )
        }
    }

    @Composable
    @Preview
    fun PreviewUpdaterLogo() {
        AppTheme {
            UpdaterLogo()
        }
    }

    @Composable
    fun MainScreenTopContent(
        buildDate: String,
        buildVersion: String,
        isCheckingForUpdate: State<Boolean>,
        lastCheckedTime: State<String?>,
        onUpdateCheckRequest: () -> Unit,
    ) {
        UpdaterLogo()
        Text(
            modifier = Modifier.padding(top = 32.dp),
            text = stringResource(
                id = R.string.system_build_info_format,
                buildDate,
                buildVersion
            ),
            fontWeight = FontWeight.Bold
        )
        CustomButton(
            modifier = Modifier.padding(top = 32.dp),
            enabled = !isCheckingForUpdate.value,
            text = stringResource(id = R.string.check_for_updates),
            onClick = onUpdateCheckRequest
        )
        if (isCheckingForUpdate.value) {
            Text(
                modifier = Modifier.padding(top = 32.dp),
                text = stringResource(id = R.string.checking_for_update)
            )
        } else {
            lastCheckedTime.value?.let {
                Text(
                    modifier = Modifier.padding(top = 32.dp),
                    text = stringResource(
                        id = R.string.last_checked_time_format,
                        it
                    )
                )
            }
        }
    }

    @Composable
    fun NoUpdateCard() {
        CardContent(
            title = stringResource(id = R.string.up_to_date),
            subtitle = stringResource(id = R.string.come_back_later)
        )
    }

    companion object {
        private const val TOP_CONTENT_HEIGHT_FRACTION = 0.6f
        private val ZIP_MIME = arrayOf("application/zip")

        private const val TAG = "MainActivity"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}