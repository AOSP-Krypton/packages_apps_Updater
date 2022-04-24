/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.data.update

import android.content.Context
import android.os.*
import android.os.UpdateEngine.ErrorCodeConstants
import android.os.UpdateEngine.UpdateStatusConstants
import android.util.Log

import com.krypton.updater.R
import com.krypton.updater.data.BatteryMonitor

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    applicationScope: CoroutineScope,
    private val otaFileManager: OTAFileManager,
    private val updateEngine: UpdateEngine,
    private val batteryMonitor: BatteryMonitor,
) {
    private val updateEngineCallback = object : UpdateEngineCallback() {
        override fun onStatusUpdate(status: Int, percent: Float) {
            logD("onStatusUpdate, status = $status, percent = $percent")
            when (status) {
                UpdateStatusConstants.IDLE,
                UpdateStatusConstants.CLEANUP_PREVIOUS_UPDATE -> {
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_IDLE)
                }
                UpdateStatusConstants.UPDATE_AVAILABLE -> {
                    isUpdating = true
                    progress = 0f
                    _updateState.value = UpdateState.Initializing
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_WAITING_INSTALL)
                }
                // Step 1
                UpdateStatusConstants.DOWNLOADING -> {
                    progress = (percent / 3) * 100
                    _updateState.value = UpdateState.Updating(progress)
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                // Step 2
                UpdateStatusConstants.VERIFYING -> {
                    progress = ((percent + 1) * 100) / 3
                    _updateState.value = UpdateState.Updating(progress)
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                // Step 3
                UpdateStatusConstants.FINALIZING -> {
                    progress = ((percent + 2) * 100) / 3
                    _updateState.value = UpdateState.Updating(progress)
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                UpdateStatusConstants.UPDATED_NEED_REBOOT -> {
                    _updateState.value = UpdateState.Finished
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_WAITING_REBOOT)
                }
                else -> Log.e(TAG, "onStatusUpdate: unknown status code $status")
            }
        }

        override fun onPayloadApplicationComplete(errorCode: Int) {
            logD("onPayloadApplicationComplete, errorCode = $errorCode")
            updateScheduled = false
            isUpdating = false
            when (errorCode) {
                ErrorCodeConstants.SUCCESS -> {
                    _updateState.value = UpdateState.Finished
                    systemUpdateService.updateSystemUpdateInfo(PersistableBundle().apply {
                        putInt(
                            SystemUpdateManager.KEY_STATUS,
                            SystemUpdateManager.STATUS_WAITING_REBOOT
                        )
                    })
                }
                ErrorCodeConstants.DOWNLOAD_INVALID_METADATA_MAGIC_STRING,
                ErrorCodeConstants.DOWNLOAD_METADATA_SIGNATURE_MISMATCH ->
                    reportFailure(context.getString(R.string.metadata_verification_failed))
                ErrorCodeConstants.NOT_ENOUGH_SPACE ->
                    reportFailure(context.getString(R.string.not_enough_space))
                ErrorCodeConstants.PAYLOAD_TIMESTAMP_ERROR ->
                    reportFailure(context.getString(R.string.downgrading_not_allowed))
                ErrorCodeConstants.NEW_ROOTFS_VERIFICATION_ERROR ->
                    reportFailure(context.getString(R.string.rootfs_verification_failed))
                ErrorCodeConstants.DOWNLOAD_TRANSFER_ERROR ->
                    reportFailure(context.getString(R.string.update_transfer_error))
                ErrorCodeConstants.USER_CANCELLED -> {
                    _updateState.value = UpdateState.Idle
                    updateSystemUpdateInfo(SystemUpdateManager.STATUS_IDLE)
                }
                else -> reportFailure(
                    context.getString(
                        R.string.update_installation_failed_with_code,
                        errorCode
                    )
                )
            }
        }
    }

    var isUpdating = false
        private set

    val isUpdatePaused: Boolean
        get() = _updateState.value is UpdateState.Paused

    private var updateScheduled = false

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState>
        get() = _updateState

    private var progress = 0f

    private val systemUpdateService = context.getSystemService(SystemUpdateManager::class.java)

    init {
        val updateInfo = systemUpdateService.retrieveSystemUpdateInfo()
        when (updateInfo.getInt(SystemUpdateManager.KEY_STATUS)) {
            SystemUpdateManager.STATUS_WAITING_REBOOT -> {
                _updateState.value = UpdateState.Finished
            }
            SystemUpdateManager.STATUS_IN_PROGRESS -> {
                _updateState.value = UpdateState.Updating(0f)
                updateEngine.bind(updateEngineCallback)
            }
        }
        applicationScope.launch {
            batteryMonitor.batteryState.collect {
                if (!it && isUpdating) {
                    cancel()
                    logAndUpdateState(context.getString(R.string.low_battery_plug_in))
                }
            }
        }
    }

    private fun updateSystemUpdateInfo(statusCode: Int) {
        systemUpdateService.updateSystemUpdateInfo(PersistableBundle().apply {
            putInt(
                SystemUpdateManager.KEY_STATUS,
                statusCode
            )
        })
    }

    private fun reportFailure(msg: String) {
        _updateState.value = UpdateState.Failed(progress, Throwable(msg))
        updateSystemUpdateInfo(SystemUpdateManager.STATUS_IDLE)
    }

    fun start() {
        logD("start: updateScheduled = $updateScheduled")
        if (updateScheduled) return
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        _updateState.value = UpdateState.Initializing
        val payloadInfoResult =
            PayloadInfo.Factory.createPayloadInfo(context, otaFileManager.otaFileUri)
        if (payloadInfoResult.isFailure) {
            logAndUpdateState(
                context.getString(
                    R.string.payload_generation_failed,
                    payloadInfoResult.exceptionOrNull()?.localizedMessage
                )
            )
            return
        }
        val payloadInfo = payloadInfoResult.getOrThrow()
        updateEngine.setPerformanceMode(true)
        updateEngine.bind(updateEngineCallback)
        try {
            updateEngine.applyPayload(
                payloadInfo.filePath,
                payloadInfo.offset,
                payloadInfo.size,
                payloadInfo.headerKeyValuePairs
            )
            updateScheduled = true
        } catch (e: ServiceSpecificException) {
            logAndUpdateState(context.getString(R.string.applying_payload_failed, e.message))
        }
    }

    private fun logAndUpdateState(msg: String) {
        val tr = Throwable(msg)
        Log.e(TAG, msg)
        _updateState.value = UpdateState.Failed(progress, tr)
    }

    fun pause() {
        logD("Pause: updateScheduled = $updateScheduled, isUpdatePaused = $isUpdatePaused")
        if (!updateScheduled || isUpdatePaused) return
        try {
            logD("Suspending update engine")
            updateEngine.suspend()
            _updateState.value = UpdateState.Paused(progress)
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to suspend update", e)
        }
    }

    fun resume() {
        logD("Resume: updateScheduled = $updateScheduled, isUpdatePaused = $isUpdatePaused")
        if (!updateScheduled || !isUpdatePaused) return
        try {
            logD("Resuming update engine")
            updateEngine.resume()
            _updateState.value = UpdateState.Updating(0f)
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to resume update", e)
        }
    }

    fun cancel() {
        logD("Cancel")
        try {
            updateEngine.cancel()
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to cancel partially: ${e.message}")
        } finally {
            updateEngine.apply {
                unbind()
                setPerformanceMode(false)
                cleanupAppliedPayload()
                resetStatus()
            }
        }
        reset()
    }

    fun reset() {
        updateScheduled = false
        isUpdating = false
        _updateState.value = UpdateState.Idle
        updateSystemUpdateInfo(SystemUpdateManager.STATUS_IDLE)
    }

    fun restoreUpdateFinishedState() {
        isUpdating = true
        updateScheduled = true
        _updateState.value = UpdateState.Finished
    }

    companion object {
        private const val TAG = "UpdateManager"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
