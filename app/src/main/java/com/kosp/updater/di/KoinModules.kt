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

package com.kosp.updater.di

import android.os.UpdateEngine

import androidx.room.Room

import com.kosp.updater.UpdaterApp
import com.kosp.updater.data.AB
import com.kosp.updater.data.BatteryMonitor
import com.kosp.updater.data.FileExportManager
import com.kosp.updater.data.GithubApiHelper
import com.kosp.updater.data.MainRepository
import com.kosp.updater.data.UpdateChecker
import com.kosp.updater.data.download.DownloadManager
import com.kosp.updater.data.download.DownloadRepository
import com.kosp.updater.data.room.AppDatabase
import com.kosp.updater.data.settings.SettingsRepository
import com.kosp.updater.data.update.ABUpdateManager
import com.kosp.updater.data.update.AOnlyUpdateManager
import com.kosp.updater.data.update.OTAFileManager
import com.kosp.updater.data.update.UpdateRepository

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
        if (AB) {
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