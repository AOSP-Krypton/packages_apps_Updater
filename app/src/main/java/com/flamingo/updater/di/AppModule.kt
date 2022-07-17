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

package com.flamingo.updater.di

import android.content.Context
import android.os.UpdateEngine

import androidx.room.Room

import com.flamingo.updater.UpdaterApp
import com.flamingo.updater.data.BatteryMonitor
import com.flamingo.updater.data.DeviceInfo
import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.update.ABUpdateManager
import com.flamingo.updater.data.update.AOnlyUpdateManager
import com.flamingo.updater.data.update.OTAFileManager
import com.flamingo.updater.data.update.UpdateManager

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
object AppModule {
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context) = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "updater_database"
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideApplicationScope(@ApplicationContext context: Context) =
        (context as UpdaterApp).applicationScope

    @Provides
    fun provideUpdateManager(
        @ApplicationContext context: Context,
        applicationScope: CoroutineScope,
        otaFileManager: OTAFileManager,
        batteryMonitor: BatteryMonitor
    ): UpdateManager =
        if (DeviceInfo.isAB()) {
            ABUpdateManager(
                context,
                applicationScope,
                otaFileManager,
                UpdateEngine(),
                batteryMonitor
            )
        } else {
            AOnlyUpdateManager(
                context,
                applicationScope,
                otaFileManager,
                batteryMonitor
            )
        }
}