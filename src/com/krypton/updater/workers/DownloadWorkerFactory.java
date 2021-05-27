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

package com.krypton.updater.workers;

import android.content.Context;

import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.krypton.updater.model.room.AppDatabase;
import com.krypton.updater.util.NotificationHelper;
import com.krypton.updater.model.data.OTAFileManager;
import com.krypton.updater.util.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DownloadWorkerFactory extends WorkerFactory {

    private final AppDatabase database;
    private final NotificationHelper helper;
    private final OTAFileManager ofm;

    @Inject
    public DownloadWorkerFactory(AppDatabase database,
            NotificationHelper helper, OTAFileManager ofm) {
        this.database = database;
        this.helper = helper;
        this.ofm = ofm;
    }

    @Override
    public ListenableWorker createWorker(Context appContext,
            String workerClassName, WorkerParameters workerParameters) {
        return new DownloadWorker(appContext,
            workerParameters, database, helper, ofm);
    }
}
