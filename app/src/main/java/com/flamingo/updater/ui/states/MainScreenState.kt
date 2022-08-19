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

package com.flamingo.updater.ui.states

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController

import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.flamingo.updater.R
import com.flamingo.updater.data.FileCopyStatus
import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.UpdateInfo
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.download.DownloadState
import com.flamingo.updater.data.settings.SettingsRepository
import com.flamingo.updater.data.update.UpdateRepository
import com.flamingo.updater.data.update.UpdateState
import com.flamingo.updater.ui.MAIN

import java.text.DateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class MainScreenState(
    private val coroutineScope: CoroutineScope,
    private val mainRepository: MainRepository,
    private val downloadRepository: DownloadRepository,
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    val snackbarHostState: SnackbarHostState,
    val navHostController: NavHostController,
) {

    private val locale: Locale
        get() = context.resources.configuration.locales[0]

    val systemBuildDate: String
        get() = getFormattedDate(locale, time = mainRepository.systemBuildDate)

    val systemBuildVersion: String
        get() = mainRepository.systemBuildVersion

    private val showUpdateUI: Flow<Boolean>
        get() = combine(
            updateRepository.updateState,
            updateRepository.readyForUpdate,
        ) { state, readyForUpdate ->
            readyForUpdate || state !is UpdateState.Idle
        }

    val shouldAllowUpdateCheckOrLocalUpgrade: Flow<Boolean> = combine(
        downloadRepository.downloadState,
        updateRepository.updateState
    ) { downloadState, updateState ->
        downloadState !is DownloadState.Waiting &&
                downloadState !is DownloadState.Downloading &&
                updateState !is UpdateState.Initializing &&
                updateState !is UpdateState.Verifying &&
                updateState !is UpdateState.Updating
    }

    val shouldAllowClearingCache: Flow<Boolean> = downloadRepository.downloadState.map {
        it !is DownloadState.Waiting && it !is DownloadState.Downloading
    }

    val showStateRestoreDialog: StateFlow<Boolean>
        get() = downloadRepository.restoringDownloadState

    val otaFileCopyStatus: Flow<FileCopyStatus>
        get() = updateRepository.fileCopyStatus.receiveAsFlow()

    val downloadFileExportStatus: Flow<FileCopyStatus>
        get() = downloadRepository.fileCopyStatus.receiveAsFlow()

    val exportDownload: Flow<Boolean>
        get() = settingsRepository.exportDownload

    private var updateCheckJob: Job? = null

    private val _isCheckingForUpdate = MutableStateFlow(false)

    val cardState: Flow<CardState> = combine(
        mainRepository.updateInfo,
        showUpdateUI,
        _isCheckingForUpdate
    ) { updateInfo, showUpdateUI, checkingForUpdate ->
        when {
            checkingForUpdate -> CardState.Gone
            showUpdateUI -> CardState.Update
            updateInfo is UpdateInfo.NewUpdate -> CardState.Download
            updateInfo is UpdateInfo.NoUpdate ||
                    updateInfo is UpdateInfo.Unavailable -> CardState.NoUpdate
            else -> CardState.Gone
        }
    }

    val checkUpdatesContentState: Flow<CheckUpdatesContentState>
        get() = combine(
            _isCheckingForUpdate,
            mainRepository.lastCheckedTime.map { it.time }
        ) { isCheckingForUpdate, lastCheckedTime ->
            when {
                isCheckingForUpdate -> CheckUpdatesContentState.Checking
                lastCheckedTime > 0 -> CheckUpdatesContentState.LastCheckedTimeAvailable(
                    getFormattedDate(locale, DateFormat.SHORT, Date(lastCheckedTime))
                )
                else -> CheckUpdatesContentState.Gone
            }
        }

    fun checkForUpdates() {
        updateCheckJob?.let {
            if (it.isActive) it.cancel()
        }
        updateCheckJob = coroutineScope.launch {
            _isCheckingForUpdate.value = true
            downloadRepository.resetState()
            updateRepository.resetState()
            val result = mainRepository.fetchUpdateInfo()
            _isCheckingForUpdate.value = false
            if (result.isFailure) {
                snackbarHostState.showSnackbar(
                    result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.update_check_failed)
                )
            }
        }
    }

    fun startLocalUpgrade(uri: Uri) {
        coroutineScope.launch {
            if (shouldAllowUpdateCheckOrLocalUpgrade.first()) {
                updateRepository.copyOTAFile(uri)
            }
        }
    }

    fun openSettings() {
        navHostController.navigate(MAIN.SETTINGS.path)
    }

    fun showSnackBar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openDownloads() {
        coroutineScope.launch {
            val uriResult = mainRepository.getExportDirectoryUri()
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
        coroutineScope.launch {
            downloadRepository.clearCache()
        }
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
    mainRepository: MainRepository = get(),
    downloadRepository: DownloadRepository = get(),
    updateRepository: UpdateRepository = get(),
    settingsRepository: SettingsRepository = get(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current,
    navHostController: NavHostController = rememberAnimatedNavController()
) = remember(
    mainRepository,
    downloadRepository,
    updateRepository,
    settingsRepository,
    coroutineScope,
    context,
    navHostController
) {
    MainScreenState(
        coroutineScope = coroutineScope,
        mainRepository = mainRepository,
        downloadRepository = downloadRepository,
        updateRepository = updateRepository,
        settingsRepository = settingsRepository,
        context = context,
        navHostController = navHostController,
        snackbarHostState = snackbarHostState
    )
}