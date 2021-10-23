/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.model.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

import io.reactivex.rxjava3.processors.BehaviorProcessor

import javax.inject.Inject
import javax.inject.Singleton

private const val TAG: String = "BatteryMonitor"
private const val DEBUG: Boolean = false

/**
 * Monitors the battery status (battery low or pluggged in)
 * Upon receiving the system broadcast, a boolean value is pushed
 * to batteryOkayProcessor, which is true iff battery
 * is not below config_lowBatteryWarningLevel or is plugged in.
 */
@Singleton
class BatteryMonitor @Inject constructor(ctx: Context) {
    private val context: Context
    private val batteryManager: BatteryManager
    private val batteryOkayProcessor: BehaviorProcessor<Boolean>
    private val lowBatteryPercent: Int
    private var isBatteryLow: Boolean = false
    private var isChargerConnected: Boolean = false

    init {
        context = ctx
        lowBatteryPercent = context.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel)
        batteryManager = context.getSystemService(BatteryManager::class.java)
        batteryOkayProcessor = BehaviorProcessor.createDefault(isBatteryOkay())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        logD("registering BroadcastReceiver")
        context.registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logD("onReceive, action = ${intent.getAction()}")
                batteryOkayProcessor.onNext(isBatteryOkay())
            }
        }, filter)
    }

    // Returns a BehaviorProcessor to listen for updates
    fun getBatteryOkayProcessor(): BehaviorProcessor<Boolean> = batteryOkayProcessor

    /*
     * Check whether battery is above config_lowBatteryWarningLevel
     * or if not whether it's plugged in
     */
    @Synchronized
    fun isBatteryOkay(): Boolean {
        updateStatus()
        return !isBatteryLow || isChargerConnected
    }

    private fun updateStatus() {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        logD("level = $level")
        isBatteryLow = level <= lowBatteryPercent
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isChargerConnected = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        logD("charging = $isChargerConnected")
    }

    companion object {
        fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
