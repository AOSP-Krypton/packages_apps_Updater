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

package com.krypton.updater.ui.states

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.krypton.updater.R
import com.krypton.updater.data.FileCopyStatus
import com.krypton.updater.data.download.DownloadState
import com.krypton.updater.data.update.UpdateState
import com.krypton.updater.ui.Routes
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel
import com.krypton.updater.viewmodel.UpdateViewModel

import java.text.DateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainScreenState(
    private val coroutineScope: CoroutineScope,
    private val mainViewModel: MainViewModel,
    private val downloadViewModel: DownloadViewModel,
    private val updateViewModel: UpdateViewModel,
    private val context: Context,
    val snackbarHostState: SnackbarHostState,
    val navHostController: NavHostController,
) {

    private val locale: Locale
        get() = context.resources.configuration.locales[0]

    val systemBuildDate: String
        get() = getFormattedDate(locale, time = mainViewModel.systemBuildDate)

    val systemBuildVersion: String
        get() = mainViewModel.systemBuildVersion

    val cardState: Flow<CardState>
        get() = combine(
            mainViewModel.updateResultAvailable,
            mainViewModel.updateAvailable,
            updateViewModel.showUpdateUI
        ) { updateResultAvailable, updateAvailable, showUpdateUI ->
            when {
                showUpdateUI -> CardState.Update
                updateAvailable -> CardState.Download
                updateResultAvailable -> CardState.NoUpdate
                else -> CardState.Gone
            }
        }

    val shouldAllowUpdateCheckOrLocalUpgrade: Flow<Boolean>
        get() = combine(
            downloadViewModel.downloadState,
            updateViewModel.updateState
        ) { downloadState, updateState ->
            downloadState !is DownloadState.Waiting &&
                    downloadState !is DownloadState.Downloading &&
                    updateState !is UpdateState.Initializing &&
                    updateState !is UpdateState.Verifying &&
                    updateState !is UpdateState.Updating
        }

    val shouldAllowClearingCache: Flow<Boolean>
        get() = downloadViewModel.downloadState.map {
            it !is DownloadState.Waiting && it !is DownloadState.Downloading
        }

    val showStateRestoreDialog: StateFlow<Boolean>
        get() = downloadViewModel.restoringDownloadState

    val checkUpdatesContentState: Flow<CheckUpdatesContentState>
        get() = combine(
            mainViewModel.isCheckingForUpdate,
            mainViewModel.lastCheckedTime
        ) { isCheckingForUpdate, lastCheckedTime ->
            when {
                isCheckingForUpdate -> CheckUpdatesContentState.Checking
                lastCheckedTime > 0 -> CheckUpdatesContentState.LastCheckedTimeAvailable(
                    getFormattedDate(locale, DateFormat.SHORT, Date(lastCheckedTime))
                )
                else -> CheckUpdatesContentState.Gone
            }
        }

    val otaFileCopyStatus: Flow<FileCopyStatus>
        get() = updateViewModel.fileCopyStatus.receiveAsFlow()

    val downloadFileExportStatus: Flow<FileCopyStatus>
        get() = downloadViewModel.fileCopyStatus.receiveAsFlow()

    init {
        coroutineScope.launch {
            for (event in mainViewModel.updateFailedEvent) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        event ?: context.getString(R.string.update_check_failed)
                    )
                }
            }
        }
    }

    fun checkForUpdates() {
        mainViewModel.checkForUpdates()
    }

    fun startLocalUpgrade(uri: Uri) {
        updateViewModel.setupLocalUpgrade(uri)
    }

    fun openSettings() {
        navHostController.navigate(Routes.SETTINGS)
    }

    fun showSnackBar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openDownloads() {
        coroutineScope.launch {
            val uriResult = mainViewModel.getDownloadsExportDirectoryUri()
            if (uriResult.isSuccess) {
                val intent = Intent(Intent.ACTION_VIEW, uriResult.getOrThrow())
                val resolvedActivities =
                    context.packageManager.queryIntentActivities(intent, 0 /* flags */)
                if (resolvedActivities.isNotEmpty()) {
                    context.startActivity(intent)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.activity_not_found))
                }
            } else {
                snackbarHostState.showSnackbar(
                    uriResult.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.failed_to_acquire_uri)
                )
            }
        }
    }

    fun clearCache() {
        mainViewModel.clearCache()
    }

    companion object {
        private fun getFormattedDate(
            locale: Locale,
            timeStyle: Int = -1,
            time: Date,
        ) = if (timeStyle == -1) {
            DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(time)
        } else {
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, timeStyle, locale).format(time)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun rememberMainScreenState(
    mainViewModel: MainViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current,
    navHostController: NavHostController = rememberAnimatedNavController()
) = remember(
    mainViewModel,
    downloadViewModel,
    updateViewModel,
    snackbarHostState,
    coroutineScope,
    context,
    navHostController
) {
    MainScreenState(
        coroutineScope,
        mainViewModel,
        downloadViewModel,
        updateViewModel,
        context,
        snackbarHostState,
        navHostController
    )
}