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

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;

import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.model.repos.DownloadRepository;
import com.krypton.updater.model.room.DownloadStatusEntity;
import com.krypton.updater.R;
import com.krypton.updater.util.Utils;
import com.krypton.updater.UpdaterApplication;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class DownloadViewModel extends AndroidViewModel {

    private final DownloadRepository repository;
    private final MediatorLiveData<ProgressInfo> progressInfo;
    private final MutableLiveData<Boolean> viewVisibility, controlVisibility, pauseStatus;
    private Disposable disposable;
    private boolean paused = false;

    public DownloadViewModel(Application application) {
        super(application);
        repository = ((UpdaterApplication) application)
            .getComponent().getDownloadRepository();
        progressInfo = new MediatorLiveData<>();
        viewVisibility = new MutableLiveData<>(new Boolean(false));
        controlVisibility = new MutableLiveData<>(new Boolean(true));
        pauseStatus = new MutableLiveData<>(new Boolean(false));
        observeProgress();
    }

    @Override
    public void onCleared() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public LiveData<ProgressInfo> getDownloadProgress() {
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

    public void startDownload() {
        repository.startDownload();
    }

    public void pauseDownload() {
        paused = !paused;
        pauseStatus.setValue(new Boolean(paused));
        repository.pauseDownload();
    }

    public void cancelDownload() {
        repository.cancelDownload();
    }

    private void observeProgress() {
        disposable = repository.getUUIDSubject()
            .distinctUntilChanged()
            .filter(id -> id != null)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(id ->
                progressInfo.addSource(repository.getWorkManager()
                    .getWorkInfoByIdLiveData(id), workInfo -> {
                        if (workInfo == null) {
                            return;
                        }
                        final State state = workInfo.getState();
                        if (state == State.CANCELLED && !paused) {
                            viewVisibility.postValue(new Boolean(false));
                        }
                        ProgressInfo info = repository.getProgressInfo(state);
                        if (info != null) {
                            progressInfo.postValue(info);
                        }
                    })
                );

        progressInfo.addSource(LiveDataReactiveStreams.fromPublisher(
            repository.getDatabaseFlowable().filter(entity -> entity != null)),
                entity -> {
                    paused = entity.status == PAUSED;
                    pauseStatus.setValue(new Boolean(paused));
                    if (viewVisibility.getValue()) {
                        if (entity.status == CANCELLED) {
                            viewVisibility.postValue(new Boolean(false));
                        }
                    } else {
                        if (entity.status >= INDETERMINATE) {
                            viewVisibility.postValue(new Boolean(true));
                        }
                    }
                    if (controlVisibility.getValue()) {
                        if (entity.status == FINISHED || entity.status == FAILED) {
                            controlVisibility.postValue(new Boolean(false));
                        }
                    }
                    progressInfo.postValue(repository.getProgressInfo(entity));
                });
    }
}
