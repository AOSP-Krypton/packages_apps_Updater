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

import static com.krypton.updater.util.Constants.REBOOT_PENDING;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Keep;

import com.krypton.updater.model.room.AppDatabase;
import com.krypton.updater.model.room.GlobalStatusDao;
import com.krypton.updater.model.room.GlobalStatusEntity;
import com.krypton.updater.util.Utils;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

@Keep
public class BootCompletedReceiver extends BroadcastReceiver {
    @Inject
    public void inject(ExecutorService executor, AppDatabase database) {
        executor.execute(() -> {
            final GlobalStatusDao dao = database.getGlobalStatusDao();
            final GlobalStatusEntity entity = dao.getCurrentStatus();
            if (entity != null && entity.status == REBOOT_PENDING) {
                dao.insert(new GlobalStatusEntity());
                database.getDownloadStatusDao().deleteTable();
            }
        });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ((UpdaterApplication) context.getApplicationContext())
            .getComponent().inject(this);
    }
}
