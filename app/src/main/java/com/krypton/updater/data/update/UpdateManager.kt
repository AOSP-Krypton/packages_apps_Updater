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
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemUpdateManager
import android.util.Log

import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class UpdateManager protected constructor(
    @ApplicationContext context: Context,
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

    private val systemUpdateService = context.getSystemService(SystemUpdateManager::class.java)

    protected fun updateSystemUpdateInfo(statusCode: Int) {
        systemUpdateService.updateSystemUpdateInfo(PersistableBundle().apply {
            putInt(
                SystemUpdateManager.KEY_STATUS,
                statusCode
            )
        })
    }

    protected fun getSystemUpdateInfo(): Bundle {
        return systemUpdateService.retrieveSystemUpdateInfo()
    }

    protected fun reportFailure(msg: String) {
        updateStateInternal.value = UpdateState.Failed(progress, Throwable(msg))
        updateSystemUpdateInfo(SystemUpdateManager.STATUS_IDLE)
    }

    protected fun logAndUpdateState(msg: String) {
        Log.e(TAG, msg)
        updateStateInternal.value = UpdateState.Failed(progress, Throwable(msg))
    }

    abstract fun start()

    abstract fun pause()

    abstract fun resume()

    abstract fun cancel()

    abstract fun reset()

    abstract fun reboot()

    companion object {
        internal const val TAG = "UpdateManager"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        internal fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
