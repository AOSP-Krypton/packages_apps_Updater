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

import static com.krypton.updater.util.Constants.DOWNLOAD_PENDING;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static com.krypton.updater.util.Constants.REBOOT_PENDING;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MutableLiveData;

import com.krypton.updater.model.data.Response;
import com.krypton.updater.model.repos.AppRepository;
import com.krypton.updater.UpdaterApplication;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class AppViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private Disposable disposable;
    private MutableLiveData<Boolean> refreshButtonVisibility, localUpgradeButtonVisibility,
        downloadButtonVisibility, updateButtonVisibility, rebootButtonVisibility;
    private LiveData<String> localUpgradeFileName;
    private LiveData<Response> otaResponse, changelogResponse;

    public AppViewModel(Application application) {
        super(application);
        repository = ((UpdaterApplication) application)
            .getComponent().getAppRepository();
        refreshButtonVisibility = new MutableLiveData<>();
        localUpgradeButtonVisibility = new MutableLiveData<>();
        downloadButtonVisibility = new MutableLiveData<>();
        updateButtonVisibility = new MutableLiveData<>();
        rebootButtonVisibility = new MutableLiveData<>();
        localUpgradeFileName = new MutableLiveData<>();
        observe();
    }

    @Override
    public void onCleared() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public LiveData<Response> getOTAResponse() {
        if (otaResponse == null) {
            otaResponse = LiveDataReactiveStreams.fromPublisher(
                repository.getOTAResponsePublisher());
        }
        return otaResponse;
    }

    public LiveData<Response> getChangelogResponse() {
        if (changelogResponse == null) {
            changelogResponse = LiveDataReactiveStreams.fromPublisher(
                repository.getChangelogResponsePublisher());
        }
        return changelogResponse;
    }

    public void fetchBuildInfo() {
        repository.fetchBuildInfo();
    }

    public void fetchChangelog() {
        repository.fetchChangelog();
    }

    public void initiateReboot() {
        repository.resetStatusAndReboot();
    }

    public LiveData<Boolean> getRefreshButtonVisibility() {
        return refreshButtonVisibility;
    }

    public LiveData<Boolean> getLocalUpgradeButtonVisibility() {
        return localUpgradeButtonVisibility;
    }

    public LiveData<Boolean> getDownloadButtonVisibility() {
        return downloadButtonVisibility;
    }

    public LiveData<Boolean> getUpdateButtonVisibility() {
        return updateButtonVisibility;
    }

    public LiveData<Boolean> getRebootButtonVisibility() {
        return rebootButtonVisibility;
    }

    public LiveData<String> getLocalUpgradeFileName() {
        return localUpgradeFileName;
    }

    public void reset() {
        repository.resetStatus();
    }

    public void resetStatusIfNotDone() {
        repository.resetStatusIfNotDone();
    }

    private void observe() {
        disposable = repository.getGlobalStatusProcessor()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(status -> {
                final boolean statusUnknown = status == 0;
                refreshButtonVisibility.setValue(statusUnknown);
                localUpgradeButtonVisibility.setValue(statusUnknown);
                downloadButtonVisibility.setValue(status == DOWNLOAD_PENDING);
                updateButtonVisibility.setValue(status == UPDATE_PENDING);
                rebootButtonVisibility.setValue(status == REBOOT_PENDING);
            });
        localUpgradeFileName = LiveDataReactiveStreams.fromPublisher(
            repository.getLocalUpgradeFileProcessor());
    }
}
