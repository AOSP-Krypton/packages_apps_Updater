/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater

import android.app.Application

import com.flamingo.updater.di.appModule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class UpdaterApp : Application() {

    lateinit var applicationScope: CoroutineScope
        private set

    override fun onCreate() {
        super.onCreate()
        applicationScope = CoroutineScope(Dispatchers.Main)
        startKoin {
            androidContext(this@UpdaterApp)
            modules(appModule())
        }
    }
}