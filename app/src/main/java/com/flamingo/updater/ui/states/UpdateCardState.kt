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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

import com.flamingo.updater.R
import com.flamingo.updater.data.update.UpdateState
import com.flamingo.updater.services.UpdateInstallerService
import com.flamingo.updater.viewmodel.UpdateViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UpdateCardState(
    coroutineScope: CoroutineScope,
    private val viewModel: UpdateViewModel,
    private val service: UpdateInstallerService?,
    private val snackbarHostState: SnackbarHostState,
    private val context: Context
) {

    val titleText: String
        get() = context.getString(R.string.install_update)

    val shouldShowProgress: Flow<Boolean>
        get() = viewModel.updateState.map { it !is UpdateState.Idle && it !is UpdateState.Finished }

    val progress: Flow<Float>
        get() = viewModel.updateState.map {
            when (it) {
                is UpdateState.Initializing, is UpdateState.Idle -> 0f
                is UpdateState.Verifying -> it.progress
                is UpdateState.Updating -> it.progress
                is UpdateState.Paused -> it.progress
                is UpdateState.Failed -> it.progress
                is UpdateState.Finished -> 100f
            }
        }

    val progressDescriptionText: Flow<String?>
        get() = viewModel.updateState.map {
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

    val shouldShowLeadingActionButton: Flow<Boolean>
        get() = viewModel.updateState.map {
            viewModel.supportsUpdateSuspension &&
                    (it is UpdateState.Verifying ||
                            it is UpdateState.Updating ||
                            it is UpdateState.Paused)
        }

    val leadingActionButtonText: Flow<String?>
        get() = viewModel.updateState.map {
            when (it) {
                is UpdateState.Verifying, is UpdateState.Updating -> context.getString(R.string.pause)
                is UpdateState.Paused -> context.getString(R.string.resume)
                else -> null
            }
        }

    val trailingActionButtonText: Flow<String>
        get() = viewModel.updateState.map {
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
            for (reason in viewModel.updateFailedReason) {
                snackbarHostState.showSnackbar(
                    reason ?: context.getString(R.string.installation_failed),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun leadingAction() {
        viewModel.updateState.value.let {
            if (it is UpdateState.Updating || it is UpdateState.Paused) {
                service?.pauseOrResumeUpdate()
            }
        }
    }

    fun trailingAction() {
        viewModel.updateState.value.let {
            when (it) {
                is UpdateState.Verifying, is UpdateState.Updating, is UpdateState.Paused -> service?.cancelUpdate()
                is UpdateState.Idle, is UpdateState.Initializing, is UpdateState.Failed -> startUpdate()
                is UpdateState.Finished -> service?.reboot()
            }
        }
    }

    private fun startUpdate() {
        context.startService(
            Intent(context, UpdateInstallerService::class.java).apply {
                action = UpdateInstallerService.ACTION_START_UPDATE
            }
        )
    }
}

@Composable
fun rememberUpdateCardState(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current
): UpdateCardState {
    var updateInstallerService by remember { mutableStateOf<UpdateInstallerService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                updateInstallerService = (service as? UpdateInstallerService.ServiceBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                updateInstallerService = null
            }
        }
    }
    DisposableEffect(context) {
        context.bindService(
            Intent(context, UpdateInstallerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        onDispose { context.unbindService(serviceConnection) }
    }
    return remember(
        updateViewModel,
        snackbarHostState,
        coroutineScope,
        context,
        updateInstallerService
    ) {
        UpdateCardState(
            coroutineScope,
            updateViewModel,
            updateInstallerService,
            snackbarHostState,
            context
        )
    }
}