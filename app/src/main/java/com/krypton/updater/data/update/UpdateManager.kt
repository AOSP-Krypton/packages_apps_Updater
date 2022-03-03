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
import android.os.ServiceSpecificException
import android.os.UpdateEngine
import android.os.UpdateEngine.ErrorCodeConstants
import android.os.UpdateEngine.UpdateStatusConstants
import android.os.UpdateEngineCallback
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
                UpdateStatusConstants.CLEANUP_PREVIOUS_UPDATE,
                UpdateStatusConstants.UPDATE_AVAILABLE -> {
                    isUpdating = true
                    _updateState.value = UpdateState.updating()
                }
                // Step 1
                UpdateStatusConstants.DOWNLOADING -> {
                    _progressFlow.value = (percent / 3) * 100
                }
                // Step 2
                UpdateStatusConstants.VERIFYING -> {
                    _progressFlow.value = ((percent + 1) * 100) / 3
                }
                // Step 3
                UpdateStatusConstants.FINALIZING -> {
                    _progressFlow.value = ((percent + 2) * 100) / 3
                }
                UpdateStatusConstants.UPDATED_NEED_REBOOT -> {
                    _progressFlow.value = 100f
                }
                else -> Log.e(TAG, "onStatusUpdate: unknown status code $status")
            }
        }

        override fun onPayloadApplicationComplete(errorCode: Int) {
            logD("onPayloadApplicationComplete, errorCode = $errorCode")
            reset()
            when (errorCode) {
                ErrorCodeConstants.SUCCESS -> _updateState.value = UpdateState.finished()
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
                ErrorCodeConstants.USER_CANCELLED -> {}
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
        get() = _updateState.value.paused

    private var updateScheduled = false

    private val _updateState = MutableStateFlow(UpdateState.idle())
    val updateState: StateFlow<UpdateState>
        get() = _updateState

    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float>
        get() = _progressFlow

    init {
        // Partially cancel any ongoing updates we are not aware of
        cancelPartially()
        applicationScope.launch {
            batteryMonitor.batteryState.collect {
                if (!it && isUpdating) {
                    cancel()
                    logAndUpdateState(context.getString(R.string.low_battery_plug_in))
                }
            }
        }
    }

    private fun reportFailure(msg: String) {
        _updateState.value = UpdateState.failure(Throwable(msg))
    }

    fun start() {
        if (updateScheduled) return
        // Partially cancel any ongoing updates we are not aware of
        cancelPartially()
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        _updateState.value = UpdateState.initializing()
        val payloadInfoResult = PayloadInfoFactory.createPayloadInfo(otaFileManager.otaFileUri)
        if (payloadInfoResult.isFailure) {
            logAndUpdateState(
                context.getString(
                    R.string.payload_generation_failed,
                    payloadInfoResult.exceptionOrNull()?.message
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
        Log.e(TAG, tr.message!!)
        _updateState.value = UpdateState.failure(tr)
    }

    fun pause() {
        if (!updateScheduled || isUpdatePaused) return
        try {
            updateEngine.suspend()
            _updateState.value = UpdateState.paused()
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to suspend update", e)
        }
    }

    fun resume() {
        if (!updateScheduled || !isUpdatePaused) return
        try {
            updateEngine.resume()
            _updateState.value = UpdateState.updating()
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to resume update", e)
        }
    }

    fun cancel() {
        cancelPartially()
        updateEngine.apply {
            cleanupAppliedPayload()
            resetStatus()
        }
        reset()
    }

    private fun cancelPartially() {
        try {
            updateEngine.apply {
                cancel()
                unbind()
                setPerformanceMode(false)
            }
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to cancel partially: ${e.message}")
        }
    }

    fun reset() {
        updateScheduled = false
        isUpdating = false
        _updateState.value = UpdateState.idle()
        _progressFlow.value = 0f
    }

    fun restoreUpdateFinishedState() {
        isUpdating = true
        updateScheduled = true
        _updateState.value = UpdateState.finished()
        _progressFlow.value = 100f
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
