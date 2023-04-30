/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
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

package com.kosp.updater.data.update

import android.content.Context
import android.os.PowerManager
import android.os.ServiceSpecificException
import android.os.SystemUpdateManager
import android.os.UpdateEngine
import android.os.UpdateEngine.ErrorCodeConstants
import android.os.UpdateEngine.UpdateStatusConstants
import android.os.UpdateEngineCallback
import android.util.Log

import com.kosp.updater.R
import com.kosp.updater.data.BatteryMonitor

import kotlinx.coroutines.CoroutineScope

class ABUpdateManager(
    private val context: Context,
    private val otaFileManager: OTAFileManager,
    private val updateEngine: UpdateEngine,
    private val batteryMonitor: BatteryMonitor,
    applicationScope: CoroutineScope
) : UpdateManager(context, applicationScope, batteryMonitor) {

    private val updateEngineCallback = object : UpdateEngineCallback() {
        override fun onStatusUpdate(status: Int, percent: Float) {
            logD("onStatusUpdate, status = $status, percent = $percent")
            when (status) {
                UpdateStatusConstants.IDLE,
                UpdateStatusConstants.CLEANUP_PREVIOUS_UPDATE -> {
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_IDLE)
                }
                UpdateStatusConstants.UPDATE_AVAILABLE -> {
                    progress = 0f
                    updateStateInternal.value = UpdateState.Initializing
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_WAITING_INSTALL)
                }
                // Step 1
                UpdateStatusConstants.DOWNLOADING -> {
                    progress = (percent / 3) * 100
                    updateStateInternal.value = UpdateState.Verifying(progress)
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                // Step 2
                UpdateStatusConstants.VERIFYING -> {
                    progress = ((percent + 1) * 100) / 3
                    updateStateInternal.value = UpdateState.Verifying(progress)
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                // Step 3
                UpdateStatusConstants.FINALIZING -> {
                    progress = ((percent + 2) * 100) / 3
                    updateStateInternal.value = UpdateState.Updating(progress)
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_IN_PROGRESS)
                }
                UpdateStatusConstants.UPDATED_NEED_REBOOT -> {
                    updateStateInternal.value = UpdateState.Finished
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_WAITING_REBOOT)
                }
                else -> Log.e(TAG, "onStatusUpdate: unknown status code $status")
            }
        }

        override fun onPayloadApplicationComplete(errorCode: Int) {
            logD("onPayloadApplicationComplete, errorCode = $errorCode")
            updateScheduled = false
            when (errorCode) {
                ErrorCodeConstants.SUCCESS -> {
                    updateStateInternal.value = UpdateState.Finished
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_WAITING_REBOOT)
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
                    updateStateInternal.value = UpdateState.Idle
                    updateSystemUpdateStatus(SystemUpdateManager.STATUS_IDLE)
                }
                else -> reportFailure(
                    context.getString(
                        R.string.update_installation_failed_with_code,
                        errorCode
                    )
                )
            }
            releaseLock()
        }
    }

    override val supportsUpdateSuspension = true

    private var updateScheduled = false

    init {
        when (getSystemUpdateStatus()) {
            SystemUpdateManager.STATUS_WAITING_REBOOT -> {
                updateStateInternal.value = UpdateState.Finished
            }
            SystemUpdateManager.STATUS_IN_PROGRESS -> {
                if (!batteryMonitor.isBatteryOkay()) {
                    logAndUpdateState(context.getString(R.string.low_battery_plug_in))
                    cancel()
                } else {
                    updateScheduled = true
                    updateStateInternal.value = UpdateState.Updating(0f)
                    updateEngine.bind(updateEngineCallback)
                    acquireLock()
                }
            }
        }
    }

    override fun start() {
        super.start()
        logD("start: updateScheduled = $updateScheduled")
        if (updateScheduled) return
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        updateStateInternal.value = UpdateState.Initializing
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
        updateEngine.apply {
            setPerformanceMode(true)
            bind(updateEngineCallback)
        }
        try {
            updateEngine.applyPayload(
                payloadInfo.filePath,
                payloadInfo.offset,
                payloadInfo.size,
                payloadInfo.headerKeyValuePairs
            )
            updateScheduled = true
            acquireLock()
        } catch (e: ServiceSpecificException) {
            logAndUpdateState(context.getString(R.string.applying_payload_failed, e.message))
        }
    }

    override fun pause() {
        super.pause()
        logD("Pause: updateScheduled = $updateScheduled, isUpdatePaused = $isUpdatePaused")
        if (!updateScheduled || isUpdatePaused) return
        try {
            logD("Suspending update engine")
            updateEngine.suspend()
            updateStateInternal.value = UpdateState.Paused(progress)
            releaseLock()
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to suspend update", e)
        }
    }

    override fun resume() {
        super.resume()
        logD("Resume: updateScheduled = $updateScheduled, isUpdatePaused = $isUpdatePaused")
        if (!updateScheduled || !isUpdatePaused) return
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        try {
            logD("Resuming update engine")
            updateEngine.resume()
            updateStateInternal.value = UpdateState.Updating(0f)
            acquireLock()
        } catch (e: ServiceSpecificException) {
            Log.e(TAG, "Failed to resume update", e)
        }
    }

    override fun cancel() {
        super.cancel()
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
        releaseLock()
        reset()
    }

    override fun reset() {
        updateScheduled = false
        updateStateInternal.value = UpdateState.Idle
        updateSystemUpdateStatus(SystemUpdateManager.STATUS_IDLE)
        releaseLock()
    }

    override fun reboot() {
        context.getSystemService(PowerManager::class.java).reboot(null)
    }
}
