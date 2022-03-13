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

import android.content.res.Resources
import android.net.Uri
import androidx.compose.material3.SnackbarDuration

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.krypton.updater.R
import com.krypton.updater.data.FileCopyStatus
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
    coroutineScope: CoroutineScope,
    private val mainViewModel: MainViewModel,
    private val downloadViewModel: DownloadViewModel,
    private val updateViewModel: UpdateViewModel,
    private val resources: Resources,
    val snackbarHostState: SnackbarHostState,
) {

    private val locale: Locale
        get() = resources.configuration.locales[0]

    val systemBuildDate: String
        get() = getFormattedDate(locale, time = mainViewModel.systemBuildDate)

    val systemBuildVersion: String
        get() = mainViewModel.systemBuildVersion

    val lastCheckedTime: Flow<String>
        get() = mainViewModel.lastCheckedTimeNew.map {
            getFormattedDate(locale, DateFormat.SHORT, Date(it))
        }

    val isCheckingForUpdate: StateFlow<Boolean>
        get() = mainViewModel.isCheckingForUpdate

    val showUpdateUI: Flow<Boolean>
        get() = updateViewModel.showUpdateUI

    val showDownloadUI: Flow<Boolean>
        get() = mainViewModel.updateAvailable

    val shouldShowCard: Flow<Boolean>
        get() = combine(
            showUpdateUI,
            mainViewModel.updateResultAvailable
        ) { showUpdateUI, updateResultAvailable ->
            showUpdateUI || updateResultAvailable
        }

    private val _showExportingDialog = MutableStateFlow(false)
    val showExportingDialog: StateFlow<Boolean>
        get() = _showExportingDialog

    private val _showCopyingDialog = MutableStateFlow(false)
    val showCopyingDialog: StateFlow<Boolean>
        get() = _showCopyingDialog

    init {
        coroutineScope.launch {
            for (event in mainViewModel.updateFailedEvent) {
                snackbarHostState.showSnackbar(
                    event ?: resources.getString(R.string.update_check_failed)
                )
            }
        }
        coroutineScope.launch {
            receiveExportEvents()
        }
        coroutineScope.launch {
            receiveCopyEvents()
        }
    }

    private suspend fun receiveExportEvents() {
        for (status in downloadViewModel.fileCopyStatus) {
            when (status) {
                is FileCopyStatus.Copying -> _showExportingDialog.value = true
                is FileCopyStatus.Success -> {
                    _showExportingDialog.value = false
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.exported_file_successfully
                        ),
                        duration = SnackbarDuration.Short
                    )
                }
                is FileCopyStatus.Failure -> {
                    _showExportingDialog.value = false
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.exporting_failed,
                            status.reason
                        ),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    private suspend fun receiveCopyEvents() {
        for (status in updateViewModel.fileCopyStatus) {
            when (status) {
                is FileCopyStatus.Copying -> {
                    _showCopyingDialog.value = true
                }
                is FileCopyStatus.Success -> {
                    _showCopyingDialog.value = false
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.successfully_copied),
                        duration = SnackbarDuration.Short
                    )
                }
                is FileCopyStatus.Failure -> {
                    _showCopyingDialog.value = false
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.copying_failed,
                            status.reason
                        )
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

@Composable
fun rememberMainScreenState(
    mainViewModel: MainViewModel,
    downloadViewModel: DownloadViewModel,
    updateViewModel: UpdateViewModel,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    resources: Resources = LocalContext.current.resources
) = remember(
    mainViewModel,
    downloadViewModel,
    updateViewModel,
    snackbarHostState,
    resources
) {
    MainScreenState(
        coroutineScope,
        mainViewModel,
        downloadViewModel,
        updateViewModel,
        resources,
        snackbarHostState,
    )
}