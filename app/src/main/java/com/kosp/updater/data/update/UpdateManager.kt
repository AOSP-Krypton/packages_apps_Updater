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
import android.os.PersistableBundle
import android.os.PowerManager
import android.os.SystemUpdateManager
import android.util.Log

import androidx.core.content.getSystemService

import com.kosp.updater.R
import com.kosp.updater.data.BatteryMonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UpdateManager(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val batteryMonitor: BatteryMonitor
) {
    protected val updateStateInternal = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState>
        get() = updateStateInternal

    val isUpdating: Boolean
        get() = updateStateInternal.value.let {
            it !is UpdateState.Idle &&
                    it !is UpdateState.Failed &&
                    it !is UpdateState.Finished
        }

    val isUpdatePaused: Boolean
        get() = updateStateInternal.value is UpdateState.Paused

    abstract val supportsUpdateSuspension: Boolean

    protected var progress = 0f

    private val systemUpdateService = context.getSystemService<SystemUpdateManager>()!!
    private val powerManager = context.getSystemService<PowerManager>()!!

    private val wakeLock: PowerManager.WakeLock? = if (powerManager.isWakeLockLevelSupported(PowerManager.PARTIAL_WAKE_LOCK)) {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, UPDATE_WAKELOCK_TAG)
    } else {
        null
    }

    private var batteryMonitorJob: Job? = null

    protected fun acquireLock() {
        if (wakeLock != null && !wakeLock.isHeld) {
            wakeLock.acquire(WAKELOCK_TIMEOUT)
        }
    }

    protected fun releaseLock() {
        if (wakeLock != null && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    protected fun updateSystemUpdateStatus(statusCode: Int) {
        systemUpdateService.updateSystemUpdateInfo(PersistableBundle().apply {
            putInt(
                SystemUpdateManager.KEY_STATUS,
                statusCode
            )
        })
    }

    protected fun getSystemUpdateStatus(): Int {
        return systemUpdateService.retrieveSystemUpdateInfo()
            .getInt(SystemUpdateManager.KEY_STATUS)
    }

    protected fun reportFailure(msg: String) {
        updateStateInternal.value = UpdateState.Failed(progress, Throwable(msg))
        updateSystemUpdateStatus(SystemUpdateManager.STATUS_IDLE)
    }

    protected fun logAndUpdateState(msg: String) {
        Log.e(TAG, msg)
        updateStateInternal.value = UpdateState.Failed(progress, Throwable(msg))
    }

    open fun start() {
        monitorBattery()
    }

    open fun pause() {
        stopMonitoringBattery()
    }

    open fun resume() {
        monitorBattery()
    }

    open fun cancel() {
        stopMonitoringBattery()
    }

    abstract fun reset()

    abstract fun reboot()

    private fun monitorBattery() {
        if (batteryMonitorJob?.isActive != true) {
            batteryMonitorJob = applicationScope.launch {
                batteryMonitor.batteryState.collect {
                    if (!it && isUpdating) {
                        cancel()
                        logAndUpdateState(context.getString(R.string.low_battery_plug_in))
                    }
                }
            }
        }
    }

    private fun stopMonitoringBattery() {
        if (batteryMonitorJob?.isActive == true) {
            batteryMonitorJob?.cancel()
            batteryMonitorJob = null
        }
    }

    companion object {
        internal const val TAG = "UpdateManager"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        internal fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }

        private val UPDATE_WAKELOCK_TAG = "${UpdateManager::class.simpleName!!}:WakeLock"
        private const val WAKELOCK_TIMEOUT = 20 * 60 * 1000L
    }
}
