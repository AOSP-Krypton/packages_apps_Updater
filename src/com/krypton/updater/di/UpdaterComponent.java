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

import com.krypton.updater.model.repos.*;
import com.krypton.updater.model.room.AppDatabase;
import com.krypton.updater.services.*;
import com.krypton.updater.ui.activity.UpdaterActivity;
import com.krypton.updater.ui.fragment.SettingsFragment;
import com.krypton.updater.workers.DownloadWorkerFactory;

import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {UpdaterModule.class})
public interface UpdaterComponent {
    AppRepository getAppRepository();
    DownloadRepository getDownloadRepository();
    UpdateRepository getUpdateRepository();
    DownloadWorkerFactory getDownloadWorkerFactory();
    AppDatabase getAppDatabase();
    void inject(UpdateCheckerService service);
    void inject(UpdateInstallerService service);
    void inject(UpdaterActivity activity);
    void inject(SettingsFragment fragment);
}
