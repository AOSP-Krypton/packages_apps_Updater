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

package com.krypton.updater

import android.app.Application
import android.util.Log.ERROR

import androidx.work.Configuration
import androidx.work.Configuration.Provider

import com.krypton.updater.di.DaggerUpdaterComponent
import com.krypton.updater.di.UpdaterComponent
import com.krypton.updater.di.UpdaterModule

class UpdaterApplication(): Application(), Provider {

    private lateinit var component: UpdaterComponent

    override fun onCreate() {
        super.onCreate()
        component = DaggerUpdaterComponent.builder()
            .updaterModule(UpdaterModule(getApplicationContext()))
            .build()
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(ERROR)
            .setWorkerFactory(component.getDownloadWorkerFactory())
            .build()

    // Returns an instance of DaggerUpdaterComponent
    fun getComponent() = component

    // TODO : remove JvmStatic annotaion once everything is in kotlin
    companion object {
        private var isUIVisible: Boolean = false

        @JvmStatic
        fun isUIVisible() = isUIVisible

        @JvmStatic
        fun setUIVisible(visible: Boolean) {
            isUIVisible = visible
        }
    }
}
