/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.model.data;

import static android.content.Intent.ACTION_BATTERY_LOW;
import static android.content.Intent.ACTION_BATTERY_OKAY;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Monitors the battery status (battery low or pluggged in)
 * Upon receiving the system broadcast, a boolean value is pushed
 * to batteryOkayProcessor, which is true if and only if battery
 * is not below 15% or is plugged in. 
 */
@Singleton
public class BatteryMonitor {
    private static final String TAG = "BatteryMonitor";
    private static final boolean DEBUG = false;
    private static final int LOW_BATTERY_PERCENT = 15;
    private final BehaviorProcessor<Boolean> batteryOkayProcessor;
    private boolean isBatteryLow = false;
    private boolean isChargerConnected;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logD("onReceive, action = " + action);
            switch (action) {
                case ACTION_BATTERY_LOW:
                    isBatteryLow = true;
                    break;
                case ACTION_BATTERY_OKAY:
                    isBatteryLow = false;
                    break;
                case ACTION_POWER_CONNECTED:
                    isChargerConnected = true;
                    break;
                case ACTION_POWER_DISCONNECTED:
                    isChargerConnected = false;
            }
            batteryOkayProcessor.onNext(!isBatteryLow || isChargerConnected);
        }
    };

    @Inject
    public BatteryMonitor(Context context) {
        final BatteryManager batteryManager = context.getSystemService(BatteryManager.class);
        int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        logD("level = " + level);
        batteryOkayProcessor = BehaviorProcessor.createDefault(
            (level > LOW_BATTERY_PERCENT) || batteryManager.isCharging());
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BATTERY_LOW);
        filter.addAction(ACTION_BATTERY_OKAY);
        filter.addAction(ACTION_POWER_CONNECTED);
        filter.addAction(ACTION_POWER_DISCONNECTED);
        logD("registering BroadcastReceiver");
        context.registerReceiver(broadcastReceiver, filter);
    }

    public BehaviorProcessor<Boolean> getBatteryOkayProcessor() {
        return batteryOkayProcessor;
    }

    private static void logD(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
