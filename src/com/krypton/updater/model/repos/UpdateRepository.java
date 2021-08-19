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

package com.krypton.updater.model.repos;

import static android.os.UserHandle.SYSTEM;
import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.FAILED;
import static com.krypton.updater.util.Constants.FINISHED;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static com.krypton.updater.util.Constants.UPDATING;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.krypton.updater.model.data.DataStore;
import com.krypton.updater.model.data.OTAFileManager;
import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.model.data.UpdateManager;
import com.krypton.updater.model.data.UpdateStatus;
import com.krypton.updater.R;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateRepository {
    private static final String TAG = "UpdateRepository";
    private final Context context;
    private final ExecutorService executor;
    private final UpdateManager updateManager;
    private final OTAFileManager ofm;
    private final DataStore dataStore;

    @Inject
    public UpdateRepository(Context context, UpdateManager updateManager,
            ExecutorService executor, OTAFileManager ofm,
            DataStore dataStore) {
        this.context = context;
        this.executor = executor;
        this.updateManager = updateManager;
        this.ofm = ofm;
        this.dataStore = dataStore;
    }

    public void startUpdate() {
        executor.execute(() -> updateManager.start());
    }

    public void pauseUpdate(boolean pause) {
        executor.execute(() -> {
            if (updateManager.isUpdating()) {
                updateManager.pause(pause);
            }
        });
    }

    public void cancelUpdate() {
        executor.execute(() -> {
            if (updateManager.isUpdating()) {
                updateManager.cancel();
                dataStore.setGlobalStatus(UPDATE_PENDING);
            }
        });
    }

    public BehaviorProcessor<UpdateStatus> getUpdateStatusProcessor() {
        return updateManager.getUpdateStatusProcessor();
    }

    public void setupLocalUpgrade(String fileName, Uri uri) {
        executor.execute(() -> {
            try (InputStream inStream = context.getContentResolver().openInputStream(uri)) {
                if (ofm.copyToOTAPackageDir(inStream)) {
                    dataStore.setLocalUpgradeFileName(fileName);
                    dataStore.setGlobalStatus(UPDATE_PENDING);
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException when copying file from uri " + uri.toString(), e);
            }
        });
    }

    public ProgressInfo getProgressInfo(UpdateStatus updateStatus) {
        String status = "";
        switch (updateStatus.getStatusCode()) {
            case INDETERMINATE:
                status = getString(R.string.waiting);
                break;
            case UPDATING:
                if (updateStatus.getStep() == 1) {
                    status = getString(R.string.processing_payload);
                } else if (updateStatus.getStep() == 2) {
                    status = getString(R.string.applying_update);
                }
                break;
            case PAUSED:
                status = getString(R.string.update_paused);
                break;
            case FINISHED:
                status = getString(R.string.update_finished);
                break;
            case FAILED:
                status = getString(R.string.update_failed);
        }
        status = String.format("%s\t\t%d%%", status, updateStatus.getProgress());
        return new ProgressInfo()
            .setProgress(updateStatus.getProgress())
            .setIndeterminate(updateStatus.getStatusCode() == INDETERMINATE)
            .setExtras(String.format("%s %d/2", getString(R.string.step), updateStatus.getStep()))
            .setStatus(status);
    }

    private String getString(int id) {
        return context.getString(id);
    }
}
