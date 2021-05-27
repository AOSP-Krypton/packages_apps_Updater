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

package com.krypton.updater;

import static android.util.Log.ERROR;

import android.app.Application;

import androidx.work.Configuration;
import androidx.work.Configuration.Builder;
import androidx.work.Configuration.Provider;

import com.krypton.updater.di.DaggerUpdaterComponent;
import com.krypton.updater.di.UpdaterComponent;
import com.krypton.updater.di.UpdaterModule;
import com.krypton.updater.util.Utils;

public class UpdaterApplication extends Application implements Provider {

    private UpdaterComponent component;
    private static boolean isUIVisible;

    @Override
    public void onCreate() {
        super.onCreate();
        component = DaggerUpdaterComponent.builder()
            .updaterModule(new UpdaterModule(getApplicationContext()))
            .build();
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Builder()
            .setMinimumLoggingLevel(ERROR)
            .setWorkerFactory(component.getDownloadWorkerFactory())
            .build();
    }

    public UpdaterComponent getComponent() {
        return component;
    }

    public static boolean isUIVisible() {
        return isUIVisible;
    }

    public static void setUIVisible(boolean visible) {
        isUIVisible = visible;
    }
}
