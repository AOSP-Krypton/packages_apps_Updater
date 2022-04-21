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
import android.util.DataUnit

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController

import com.krypton.updater.R
import com.krypton.updater.data.UpdateInfo
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel

import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DownloadCardState(
    private val mainViewModel: MainViewModel,
    private val downloadViewModel: DownloadViewModel,
    private val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope,
    private val resources: Resources,
    private val navHostController: NavHostController
) {

    val titleText: String
        get() = resources.getString(R.string.new_update_available)

    val subtitleText: Flow<String>
        get() = mainViewModel.updateInfo.map { it.buildInfo }.filterNotNull().map {
            resources.getString(
                R.string.new_update_description_format,
                it.version,
                getFormattedDate(resources.configuration.locales[0], time = Date(it.date)),
                formatBytes(it.fileSize)
            )
        }

    val shouldShowProgress: Flow<Boolean>
        get() = downloadViewModel.downloadState.map { !it.idle }

    val progressDescriptionText: Flow<String>
        get() = downloadViewModel.downloadState.combine(progress) { state, progress ->
            when {
                state.waiting -> resources.getString(R.string.waiting)
                state.downloading -> resources.getString(
                    R.string.download_text_format,
                    String.format("%.2f", progress)
                )
                state.finished -> resources.getString(R.string.downloading_finished)
                state.failed -> resources.getString(R.string.downloading_failed)
                else -> resources.getString(R.string.downloading)
            }
        }

    val progress: Flow<Float>
        get() = downloadViewModel.downloadProgress

    val leadingActionButtonText: String
        get() = resources.getString(R.string.changelog)

    val trailingActionButtonText: Flow<String?>
        get() = downloadViewModel.downloadState.map {
            when {
                it.idle || it.failed -> resources.getString(R.string.download)
                it.waiting || it.downloading || it.finished -> resources.getString(android.R.string.cancel)
                else -> null
            }
        }

    private val _shouldShowDownloadSourceDialog = MutableStateFlow(false)
    val shouldShowDownloadSourceDialog: StateFlow<Boolean>
        get() = _shouldShowDownloadSourceDialog

    val downloadSources: Flow<Set<String>>
        get() = mainViewModel.updateInfo
            .filter { it.type == UpdateInfo.Type.NEW_UPDATE }
            .map { it.buildInfo?.downloadSources?.keys }
            .filterNotNull()

    init {
        coroutineScope.launch {
            downloadViewModel.downloadFailedEvent.collect {
                if (!it.hasBeenHandled) {
                    snackbarHostState.showSnackbar(
                        it.getOrNull() ?: resources.getString(R.string.downloading_failed),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    fun triggerLeadingAction() {
        navHostController.navigate(Routes.CHANGELOGS)
    }

    fun triggerTrailingAction() {
        downloadViewModel.downloadState.value.let {
            when {
                it.idle || it.failed -> coroutineScope.launch {
                    val hasAlternateDownloadSource =
                        mainViewModel.updateInfo.firstOrNull()?.buildInfo?.downloadSources != null
                    if (hasAlternateDownloadSource) {
                        _shouldShowDownloadSourceDialog.value = true
                    } else {
                        startDownload()
                    }
                }
                it.waiting || it.downloading -> downloadViewModel.cancelDownload()
            }
            return@let
        }
    }

    fun dismissDownloadSourceDialog() {
        _shouldShowDownloadSourceDialog.value = false
    }

    fun startDownloadWithSource(source: String) {
        dismissDownloadSourceDialog()
        coroutineScope.launch {
            startDownload(source)
        }
    }

    private suspend fun startDownload(source: String? = null) {
        val buildInfo = mainViewModel.updateInfo.firstOrNull()?.buildInfo ?: return
        downloadViewModel.startDownload(buildInfo, source)
    }

    companion object {
        private val units = arrayOf("KiB", "MiB", "GiB")
        private val singleDecimalFmt = DecimalFormat("00.0")
        private val doubleDecimalFmt = DecimalFormat("0.00")
        private val KiB: Long = DataUnit.KIBIBYTES.toBytes(1)

        private fun formatBytes(bytes: Long): String {
            val unit: String
            var rate = (bytes / KiB).toFloat()
            var i = 0
            while (true) {
                rate /= KiB
                if (rate >= 0.9f && rate < 1) {
                    unit = units[i + 1]
                    break
                } else if (rate < 0.9f) {
                    rate *= KiB
                    unit = units[i]
                    break
                }
                i++
            }
            val formattedSize = when {
                rate < 10 -> doubleDecimalFmt.format(rate)
                rate < 100 -> singleDecimalFmt.format(rate)
                rate < 1000 -> rate.toInt().toString()
                else -> rate.toString()
            }
            return "$formattedSize $unit"
        }

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
fun rememberDownloadCardState(
    mainViewModel: MainViewModel,
    downloadViewModel: DownloadViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    resources: Resources = LocalContext.current.resources,
    navHostController: NavHostController
) = remember(
    mainViewModel,
    downloadViewModel,
    snackbarHostState,
    coroutineScope,
    resources
) {
    DownloadCardState(
        mainViewModel,
        downloadViewModel,
        snackbarHostState,
        coroutineScope,
        resources,
        navHostController
    )
}