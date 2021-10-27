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

package com.krypton.updater.di

import android.content.Context
import android.content.SharedPreferences
import android.os.UpdateEngine

import androidx.annotation.NonNull
import androidx.preference.PreferenceManager
import androidx.work.WorkManager

import dagger.Module
import dagger.Provides

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

import javax.inject.Singleton

@Module
class UpdaterModule(private val context: Context) {
    @Provides
    fun provideContext() = context

    @Singleton
    @Provides
    fun provideSharedPreferences(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Singleton
    @Provides
    fun provideUpdateEngine() = UpdateEngine()

    @Singleton
    @Provides
    fun provideExecutorService(): ExecutorService = Executors.newCachedThreadPool()

    @Singleton
    @Provides
    fun provideWorkManager() = WorkManager.getInstance(context)
}
