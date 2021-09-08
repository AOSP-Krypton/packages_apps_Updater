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

package com.krypton.updater.viewmodel;

import static com.krypton.updater.util.Constants.BATTERY_LOW;
import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.CANCELLED;
import static com.krypton.updater.util.Constants.FAILED;
import static com.krypton.updater.util.Constants.FINISHED;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.model.data.UpdateStatus;
import com.krypton.updater.model.repos.UpdateRepository;;
import com.krypton.updater.UpdaterApplication;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public class UpdateViewModel extends AndroidViewModel {
    private static final String TAG = "UpdateViewModel";
    private static final boolean DEBUG = false;
    private final UpdateRepository repository;
    private final MutableLiveData<ProgressInfo> progressInfo;
    private final MutableLiveData<Boolean> viewVisibility, controlVisibility, pauseStatus;

    public UpdateViewModel(Application application) {
        super(application);
        repository = ((UpdaterApplication) application)
            .getComponent().getUpdateRepository();
        progressInfo = new MutableLiveData<>();
        viewVisibility = new MutableLiveData<>(false);
        controlVisibility = new MutableLiveData<>(true);
        pauseStatus = new MutableLiveData<>(false);
        observeProgress();
    }

    public LiveData<ProgressInfo> getUpdateProgress() {
        return progressInfo;
    }

    public LiveData<Boolean> pauseButtonStatus() {
        return pauseStatus;
    }

    public LiveData<Boolean> getViewVisibility() {
        return viewVisibility;
    }

    public LiveData<Boolean> getControlVisibility() {
        return controlVisibility;
    }

    public void setupLocalUpgrade(String fileName, Uri uri) {
        repository.setupLocalUpgrade(fileName, uri);
    }

    private void observeProgress() {
        repository.getUpdateStatusProcessor()
            .filter(updateStatus -> updateStatus.getStatusCode() != BATTERY_LOW)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(updateStatus -> {
                final int statusCode = updateStatus.getStatusCode();
                logD("statusCode = " + statusCode);
                pauseStatus.setValue(statusCode == PAUSED);
                if (statusCode >= INDETERMINATE) {
                    viewVisibility.postValue(true);
                } else if (statusCode == 0 || statusCode == CANCELLED) {
                    viewVisibility.postValue(false);
                }
                if (statusCode >= INDETERMINATE && statusCode <= PAUSED) {
                    controlVisibility.postValue(true);
                } else {
                    controlVisibility.postValue(false);
                }
                progressInfo.postValue(repository.getProgressInfo(updateStatus));
            });
    }

    private static void logD(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
