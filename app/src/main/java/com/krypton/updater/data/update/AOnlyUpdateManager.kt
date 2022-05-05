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

package com.krypton.updater.data.update

import android.content.Context
import android.os.RecoverySystem
import android.os.SystemUpdateManager

import com.krypton.updater.R
import com.krypton.updater.data.BatteryMonitor

import dagger.hilt.android.qualifiers.ApplicationContext

import java.io.File

import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.CoroutineScope

@Singleton
class AOnlyUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    applicationScope: CoroutineScope,
    private val otaFileManager: OTAFileManager,
    private val batteryMonitor: BatteryMonitor
) : UpdateManager(context, applicationScope, batteryMonitor) {

    override val supportsUpdateSuspension = false

    private var updateScheduled = false

    private var updateThread: UpdateThread? = null

    init {
        if (getSystemUpdateStatus() == SystemUpdateManager.STATUS_WAITING_REBOOT) {
            updateStateInternal.value = UpdateState.Finished
        }
    }

    override fun start() {
        logD("start: updateScheduled = $updateScheduled")
        if (updateScheduled) return
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        updateStateInternal.value = UpdateState.Initializing
        updateThread?.interrupt()
        updateThread = UpdateThread(
            otaFileManager.otaFile
        ) {
            progress = it.toFloat()
            updateStateInternal.value = UpdateState.Verifying(progress)
        }
        acquireLock()
        val verifyResult = runCatching {
            updateThread?.let {
                it.run()
                it.join(UPDATE_THREAD_TIMEOUT)
            }
        }
        releaseLock()
        if (verifyResult.isSuccess) {
            updateSystemUpdateStatus(SystemUpdateManager.STATUS_WAITING_REBOOT)
            updateStateInternal.value = UpdateState.Finished
        } else {
            if (verifyResult.exceptionOrNull() is InterruptedException) {
                logD("Cancelled")
                return
            }
            logAndUpdateState(
                verifyResult.exceptionOrNull()?.localizedMessage ?: context.getString(
                    R.string.failed_to_verify_update_file
                )
            )
        }
    }

    override fun pause() {
        throw UnsupportedOperationException()
    }

    override fun resume() {
        throw UnsupportedOperationException()
    }

    override fun cancel() {
        updateThread?.interrupt()
        updateThread = null
        reset()
        releaseLock()
    }

    override fun reset() {
        updateScheduled = false
        updateStateInternal.value = UpdateState.Idle
        updateSystemUpdateStatus(SystemUpdateManager.STATUS_IDLE)
        releaseLock()
    }

    override fun reboot() {
        if (!batteryMonitor.isBatteryOkay()) {
            logAndUpdateState(context.getString(R.string.low_battery_plug_in))
            return
        }
        RecoverySystem.installPackage(context, otaFileManager.otaFile)
    }

    private class UpdateThread(
        private val file: File,
        private val progressListener: RecoverySystem.ProgressListener
    ) : Thread("UpdateThread") {
        override fun run() {
            RecoverySystem.verifyPackage(
                file,
                progressListener,
                null
            )
        }
    }

    companion object {
        private const val UPDATE_THREAD_TIMEOUT = 15 * 60 * 1000L // 15 minutes
    }
}
