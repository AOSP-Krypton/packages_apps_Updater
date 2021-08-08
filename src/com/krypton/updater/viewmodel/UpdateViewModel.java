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

import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.CANCELLED;
import static com.krypton.updater.util.Constants.FAILED;
import static com.krypton.updater.util.Constants.FINISHED;

import android.app.Application;
import android.net.Uri;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.model.data.UpdateStatus;
import com.krypton.updater.model.repos.UpdateRepository;;
import com.krypton.updater.UpdaterApplication;

public class UpdateViewModel extends AndroidViewModel {

    private final UpdateRepository repository;
    private final MediatorLiveData<ProgressInfo> progressInfo;
    private final MutableLiveData<Boolean> viewVisibility, controlVisibility, pauseStatus;

    public UpdateViewModel(Application application) {
        super(application);
        repository = ((UpdaterApplication) application)
            .getComponent().getUpdateRepository();
        progressInfo = new MediatorLiveData<>();
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
        final LiveData<UpdateStatus> updateStatusLiveData = LiveDataReactiveStreams
            .fromPublisher(repository.getUpdateStatusProcessor());
        progressInfo.addSource(updateStatusLiveData, updateStatus -> {
            final int statusCode = updateStatus.getStatusCode();
            pauseStatus.setValue(statusCode == PAUSED);
            if (viewVisibility.getValue()) {
                if (statusCode == 0 || statusCode == CANCELLED) {
                    viewVisibility.postValue(false);
                }
            } else {
                if (statusCode >= INDETERMINATE) {
                    viewVisibility.postValue(true);
                }
            }
            if (controlVisibility.getValue()) {
                if (statusCode < INDETERMINATE || statusCode > PAUSED) {
                    controlVisibility.postValue(false);
                }
            } else {
                if (statusCode >= INDETERMINATE && statusCode <= PAUSED) {
                    controlVisibility.postValue(true);
                }
            }
            progressInfo.postValue(repository.getProgressInfo(updateStatus));
        });
    }
}
