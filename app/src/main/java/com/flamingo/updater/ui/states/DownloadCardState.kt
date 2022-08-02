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

import android.content.res.Resources
import android.util.DataUnit

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

import com.flamingo.updater.R
import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.UpdateInfo
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.download.DownloadState
import com.flamingo.updater.ui.MAIN

import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class DownloadCardState(
    mainRepository: MainRepository,
    private val downloadRepository: DownloadRepository,
    private val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope,
    private val resources: Resources,
    private val navHostController: NavHostController
) {

    val titleText: String = resources.getString(R.string.new_update_available)

    private val newUpdateInfo: Flow<UpdateInfo.NewUpdate> =
        mainRepository.updateInfo.filterIsInstance()

    val subtitleText: Flow<String> = newUpdateInfo.map {
        resources.getString(
            R.string.new_update_description_format,
            it.buildInfo.version,
            getFormattedDate(
                resources.configuration.locales[0],
                time = Date(it.buildInfo.date)
            ),
            formatBytes(it.buildInfo.fileSize)
        )
    }

    val shouldShowProgress: Flow<Boolean> =
        downloadRepository.downloadState.map { it !is DownloadState.Idle }

    val progressDescriptionText: Flow<String?> = downloadRepository.downloadState.map {
        when (it) {
            is DownloadState.Idle -> null
            is DownloadState.Waiting -> resources.getString(R.string.waiting)
            is DownloadState.Downloading -> resources.getString(
                R.string.download_text_format,
                String.format("%.2f", it.progress)
            )
            is DownloadState.Finished -> resources.getString(R.string.downloading_finished)
            is DownloadState.Failed -> resources.getString(R.string.downloading_failed)
            is DownloadState.Retry -> resources.getString(R.string.retrying)
        }
    }

    val progress: Flow<Float> = downloadRepository.downloadState.map {
        when (it) {
            is DownloadState.Downloading -> it.progress
            is DownloadState.Finished -> 100f
            else -> 0f
        }
    }

    val leadingActionButtonText: String = resources.getString(R.string.changelog)

    val trailingActionButtonText: Flow<String?> = downloadRepository.downloadState.map {
        when (it) {
            is DownloadState.Idle, is DownloadState.Failed -> resources.getString(R.string.download)
            is DownloadState.Waiting, is DownloadState.Downloading, DownloadState.Retry -> resources.getString(
                android.R.string.cancel
            )
            is DownloadState.Finished -> null
        }
    }

    private val _shouldShowDownloadSourceDialog = MutableStateFlow(false)
    val shouldShowDownloadSourceDialog: StateFlow<Boolean> =
        _shouldShowDownloadSourceDialog.asStateFlow()

    val downloadSources: Flow<Set<String>> = newUpdateInfo
        .map { it.buildInfo.downloadSources.keys }
        .filterNotNull()

    init {
        coroutineScope.launch {
            downloadRepository.downloadState.filterIsInstance<DownloadState.Failed>().collect {
                snackbarHostState.showSnackbar(
                    it.exception?.localizedMessage
                        ?: resources.getString(R.string.downloading_failed),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun triggerLeadingAction() {
        navHostController.navigate(MAIN.CHANGELOGS.path)
    }

    fun triggerTrailingAction() {
        downloadRepository.downloadState.value.let {
            when (it) {
                is DownloadState.Idle,
                is DownloadState.Failed,
                is DownloadState.Finished -> coroutineScope.launch {
                    val sources = downloadSources.first()
                    if (sources.size > 1) {
                        _shouldShowDownloadSourceDialog.value = true
                    } else {
                        startDownload(sources.first())
                    }
                }
                is DownloadState.Waiting,
                is DownloadState.Downloading,
                is DownloadState.Retry -> downloadRepository.cancelDownload()
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

    private suspend fun startDownload(source: String) {
        val buildInfo = newUpdateInfo.firstOrNull()?.buildInfo ?: return
        downloadRepository.triggerDownload(buildInfo, source)
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
    mainRepository: MainRepository = get(),
    downloadRepository: DownloadRepository = get(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    resources: Resources = LocalContext.current.resources,
    navHostController: NavHostController = rememberNavController()
) = remember(
    mainRepository,
    downloadRepository,
    snackbarHostState,
    coroutineScope,
    resources,
    navHostController
) {
    DownloadCardState(
        mainRepository = mainRepository,
        downloadRepository = downloadRepository,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        resources = resources,
        navHostController = navHostController
    )
}