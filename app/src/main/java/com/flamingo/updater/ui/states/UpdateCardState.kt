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

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import com.flamingo.updater.R
import com.flamingo.support.compose.runtime.rememberBoundService
import com.flamingo.updater.data.update.UpdateRepository
import com.flamingo.updater.data.update.UpdateState
import com.flamingo.updater.services.UpdateInstallerService

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import org.koin.androidx.compose.get

class UpdateCardState(
    coroutineScope: CoroutineScope,
    private val updateRepository: UpdateRepository,
    private val service: UpdateInstallerService?,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context
) {

    val titleText = context.getString(R.string.install_update)

    val shouldShowProgress: Flow<Boolean> =
        updateRepository.updateState.map { it !is UpdateState.Idle && it !is UpdateState.Finished }

    val progress: Flow<Float> = updateRepository.updateState.map {
        when (it) {
            is UpdateState.Initializing, is UpdateState.Idle -> 0f
            is UpdateState.Verifying -> it.progress
            is UpdateState.Updating -> it.progress
            is UpdateState.Paused -> it.progress
            is UpdateState.Failed -> it.progress
            is UpdateState.Finished -> 100f
        }
    }

    val progressDescriptionText: Flow<String?> = updateRepository.updateState.map {
        when (it) {
            is UpdateState.Idle -> null
            is UpdateState.Initializing -> context.getString(R.string.initializing)
            is UpdateState.Verifying -> context.getString(
                R.string.verifying_update,
                String.format("%.2f", it.progress)
            )
            is UpdateState.Updating -> context.getString(
                R.string.installing_update_format,
                String.format("%.2f", it.progress)
            )
            is UpdateState.Paused -> context.getString(R.string.installation_paused)
            is UpdateState.Failed -> context.getString(R.string.installation_failed)
            is UpdateState.Finished -> context.getString(R.string.installation_finished)
        }
    }

    val shouldShowLeadingActionButton: Flow<Boolean> = updateRepository.updateState.map {
        updateRepository.supportsUpdateSuspension &&
                (it is UpdateState.Verifying ||
                        it is UpdateState.Updating ||
                        it is UpdateState.Paused)
    }

    val leadingActionButtonText: Flow<String?> = updateRepository.updateState.map {
        when (it) {
            is UpdateState.Verifying, is UpdateState.Updating -> context.getString(R.string.pause)
            is UpdateState.Paused -> context.getString(R.string.resume)
            else -> null
        }
    }

    val trailingActionButtonText: Flow<String> = updateRepository.updateState.map {
        when (it) {
            is UpdateState.Verifying,
            is UpdateState.Updating,
            is UpdateState.Paused -> {
                context.getString(android.R.string.cancel)
            }
            is UpdateState.Finished -> context.getString(R.string.reboot)
            is UpdateState.Idle,
            is UpdateState.Initializing,
            is UpdateState.Failed -> {
                context.getString(R.string.update)
            }
        }
    }

    init {
        coroutineScope.launch {
            updateRepository.updateState.filterIsInstance<UpdateState.Failed>()
                .collectLatest {
                    snackbarHostState.showSnackbar(
                        it.exception.localizedMessage
                            ?: context.getString(R.string.installation_failed),
                        duration = SnackbarDuration.Short
                    )
                }
        }
    }

    fun leadingAction() {
        updateRepository.updateState.value.let {
            if (it is UpdateState.Updating || it is UpdateState.Paused) {
                service?.pauseOrResumeUpdate()
            }
        }
    }

    fun trailingAction() {
        updateRepository.updateState.value.let {
            when (it) {
                is UpdateState.Verifying, is UpdateState.Updating, is UpdateState.Paused -> service?.cancelUpdate()
                is UpdateState.Idle, is UpdateState.Initializing, is UpdateState.Failed -> startUpdate()
                is UpdateState.Finished -> service?.reboot()
            }
        }
    }

    private fun startUpdate() {
        context.startService(Intent(context, UpdateInstallerService::class.java))
        service?.startUpdate()
    }
}

@Composable
fun rememberUpdateCardState(
    updateRepository: UpdateRepository = get(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current
): UpdateCardState {
    val updateInstallerService = rememberBoundService(
        intent = Intent(context, UpdateInstallerService::class.java),
        obtainService = {
            (it as UpdateInstallerService.ServiceBinder).service
        }
    )
    return remember(
        updateRepository,
        snackbarHostState,
        coroutineScope,
        context,
        updateInstallerService
    ) {
        UpdateCardState(
            coroutineScope,
            updateRepository,
            updateInstallerService,
            snackbarHostState,
            context
        )
    }
}