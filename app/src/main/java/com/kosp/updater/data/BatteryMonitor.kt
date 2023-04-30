/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.kosp.updater.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

import com.android.internal.R

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors the battery status.
 */
class BatteryMonitor(private val context: Context) {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val lowBatteryPercent =
        context.resources.getInteger(R.integer.config_lowBatteryWarningLevel)
    private var isBatteryLow = false
    private var isChargerConnected = false

    private val _batteryState = MutableStateFlow(true)
    val batteryState: StateFlow<Boolean>
        get() = _batteryState

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        logD("registering BroadcastReceiver")
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logD("onReceive, action = ${intent.action}")
                _batteryState.value = isBatteryOkay()
            }
        }, filter)
    }

    /**
     * Check whether battery status is okay for work.
     *
     * @return true if battery percent is above [R.integer.config_lowBatteryWarningLevel]
     *      or if not whether it's plugged in.
     */
    fun isBatteryOkay(): Boolean {
        updateStatus()
        return !isBatteryLow || isChargerConnected
    }

    private fun updateStatus() {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        logD("level = $level")
        isBatteryLow = level <= lowBatteryPercent
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isChargerConnected = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        logD("charging = $isChargerConnected")
    }

    companion object {
        private const val TAG: String = "BatteryMonitor"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
