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

package com.krypton.updater.di;

import static com.krypton.updater.util.Constants.DATABASE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UpdateEngine;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.work.WorkManager;

import com.krypton.updater.model.room.AppDatabase;

import dagger.Module;
import dagger.Provides;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import javax.inject.Singleton;

@Module
public class UpdaterModule {

    private final Context context;

    public UpdaterModule(@NonNull Context context) {
        this.context = context;
    }

    @Provides
    public Context provideContext() {
        return context;
    }

    @Singleton
    @Provides
    public AppDatabase provideAppDatabase() {
        return Room.databaseBuilder(context, AppDatabase.class, DATABASE)
            .fallbackToDestructiveMigration()
            .build();
    }

    @Singleton
    @Provides
    public SharedPreferences provideSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Singleton
    @Provides
    public UpdateEngine provideUpdateEngine() {
        return new UpdateEngine();
    }

    @Singleton
    @Provides
    public ExecutorService provideExecutorService() {
        return Executors.newCachedThreadPool();
    }

    @Singleton
    @Provides
    public WorkManager provideWorkManager() {
        return WorkManager.getInstance(context);
    }
}
