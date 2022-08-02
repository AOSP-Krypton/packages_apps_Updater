/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.di

import android.os.UpdateEngine

import androidx.room.Room

import com.flamingo.updater.UpdaterApp
import com.flamingo.updater.data.BatteryMonitor
import com.flamingo.updater.data.DeviceInfo
import com.flamingo.updater.data.FileExportManager
import com.flamingo.updater.data.GithubApiHelper
import com.flamingo.updater.data.MainRepository
import com.flamingo.updater.data.UpdateChecker
import com.flamingo.updater.data.download.DownloadManager
import com.flamingo.updater.data.download.DownloadRepository
import com.flamingo.updater.data.room.AppDatabase
import com.flamingo.updater.data.settings.SettingsRepository
import com.flamingo.updater.data.update.ABUpdateManager
import com.flamingo.updater.data.update.AOnlyUpdateManager
import com.flamingo.updater.data.update.OTAFileManager
import com.flamingo.updater.data.update.UpdateRepository

import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun appModule() = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "updater_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    single {
        (androidApplication() as UpdaterApp).applicationScope
    }

    single {
        BatteryMonitor(androidContext())
    }

    single {
        OTAFileManager(androidContext())
    }

    single {
        if (DeviceInfo.isAB()) {
            ABUpdateManager(
                context = androidContext(),
                otaFileManager = get(),
                updateEngine = UpdateEngine(),
                batteryMonitor = get(),
                applicationScope = get()
            )
        } else {
            AOnlyUpdateManager(
                context = androidContext(),
                otaFileManager = get(),
                batteryMonitor = get(),
                applicationScope = get()
            )
        }
    }

    single {
        DownloadManager(androidContext())
    }

    single {
        FileExportManager(androidContext())
    }

    single {
        DownloadRepository(
            context = androidContext(),
            applicationScope = get(),
            downloadManager = get(),
            fileExportManager = get(),
            appDatabase = get()
        )
    }

    single {
        SettingsRepository(androidContext())
    }

    single {
        UpdateRepository(
            updateManager = get(),
            otaFileManager = get(),
            downloadManager = get(),
            applicationScope = get(),
            context = androidContext(),
        )
    }

    single {
        UpdateChecker(context = androidContext(), githubApiHelper = GithubApiHelper())
    }

    single {
        MainRepository(
            context = androidContext(),
            updateChecker = get(),
            applicationScope = get(),
            fileExportManager = get(),
            appDatabase = get()
        )
    }
}