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

package com.krypton.updater.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.os.UpdateEngine.ErrorCodeConstants;
import android.os.UpdateEngine.UpdateStatusConstants;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.krypton.updater.build.BuildInfo;
import com.krypton.updater.build.PayloadInfo;
import com.krypton.updater.callbacks.ActivityCallbacks;
import com.krypton.updater.callbacks.NetworkHelperCallbacks;
import com.krypton.updater.util.NetworkHelper;
import com.krypton.updater.util.Utils;
import com.krypton.updater.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.json.JSONException;

public class UpdaterService extends Service implements NetworkHelperCallbacks {

    private Handler handler;
    private IBinder activityBinder = new ActivityBinder();
    private ActivityCallbacks callback;
    private NetworkHelper networkHelper;
    private ConnectivityManager connManager;
    private Network network;
    private ExecutorService executor;
    private Future future;
    private BuildInfo buildInfo;
    private UpdateEngine updateEngine;
    private File file;
    private boolean bound = false;
    private boolean downloadStarted, downloadPaused, downloadFinished;
    private boolean updateStarted, updatePaused, updateFinished;
    private boolean isOnline = false;
    private boolean localUpgradeMode = false;
    private long totalSize, downloadedSize;
    private int downloadProgress;
    public int updateStatus, updateProgress, updateExitCode;

    private NetworkCallback netCallback = new NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            isOnline = true;
            UpdaterService.this.network = network;
            if (downloadStarted && !downloadPaused) {
                if (future != null) {
                    future.cancel(true);
                }
                networkHelper.cleanup();
                startDownload();
            }
        }

        @Override
        public void onLost(Network network) {
            isOnline = false;
            if (!downloadFinished) {
                waitAndDispatchMessage();
            }
            UpdaterService.this.network = null;
            if (future != null) {
                future.cancel(true);
            }
            networkHelper.cleanup();
        }
    };

    private UpdateEngineCallback updateEngineCallback = new UpdateEngineCallback() {
        @Override
        public void onStatusUpdate(int status, float percent) {
            switch (status) {
                case UpdateStatusConstants.IDLE:
                case UpdateStatusConstants.CLEANUP_PREVIOUS_UPDATE:
                    break;
                case UpdateStatusConstants.UPDATE_AVAILABLE:
                    updateStarted = true;
                    if (bound) {
                        callback.onStartingUpdate();
                    }
                    break;
                case UpdateStatusConstants.DOWNLOADING:
                case UpdateStatusConstants.FINALIZING:
                    int tmpProgress = (int) (percent*100);
                    if (status != updateStatus || tmpProgress > updateProgress) {
                        updateStatus = status;
                        updateProgress = tmpProgress;
                        if (bound) {
                            callback.onStatusUpdate(updateStatus, updateProgress);
                        }
                    }
                    break;
                case UpdateStatusConstants.UPDATED_NEED_REBOOT:
                    break;
                default:
                    Utils.log("status", status);
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            updateStarted = false;
            if (errorCode != ErrorCodeConstants.USER_CANCELLED) {
                updateExitCode = errorCode;
                if (updateExitCode == ErrorCodeConstants.SUCCESS) {
                    updateFinished = true;
                }
                if (bound) {
                    callback.onFinishedUpdate(updateExitCode);
                }
            }
            updateEngine.unbind();
        }
    };

    @Override
    public void onCreate() {
        resetUpdateStatus();
        resetDownloadStatus();
        final HandlerThread thread = new HandlerThread("UpdaterService",
            Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());
        networkHelper = new NetworkHelper();
        networkHelper.setListener(this);
        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        connManager.registerDefaultNetworkCallback(netCallback);
        executor = Executors.newFixedThreadPool(4);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return activityBinder;
    }

    @Override
    public void onDestroy() {
        unregisterCallback();
        executor.shutdown();
        connManager.unregisterNetworkCallback(netCallback);
        if (updateEngine != null) {
            updateEngine.unbind();
        }
    }

    public void registerCallback(ActivityCallbacks callback) {
        this.callback = callback;
        bound = true;
        if (updateStarted || updateFinished ||
                downloadStarted || downloadFinished) {
            restoreState();
        }
    }

    public void unregisterCallback() {
        callback = null;
        bound = false;
    }

    public void updateBuildInfo() {
        executor.execute(() -> {
            if (!downloadStarted && !downloadFinished) {
                boolean foundNew = false;
                try {
                    BuildInfo tmp = networkHelper.fetchBuildInfo();
                    if (tmp != null) {
                        buildInfo = tmp;
                        foundNew = true;
                    }
                } catch (JSONException|IOException e) {
                    if (bound) {
                        callback.fetchBuildInfoFailed();
                    }
                    return;
                }

                if (foundNew) {
                    if (!localUpgradeMode) {
                        updateFile();
                    }
                    if (!checkIfAlreadyDownloaded()) {
                        if (bound) {
                            callback.onFetchedBuildInfo(buildInfo.getBundle());
                        }
                    }
                } else {
                    if (bound) {
                        callback.noUpdates();
                    }
                }
            }
        });
    }

    @Override
    public void onStartedDownload() {
        future =
            executor.submit(() -> {
                long size = networkHelper.getDownloadProgress();
                while (size != -1) {
                    if (size > downloadedSize) {
                        downloadedSize = size;
                        if (bound) {
                            callback.updateDownloadedSize(downloadedSize, totalSize);
                        }
                        int progress = (int) ((downloadedSize*100)/totalSize);
                        if (progress > downloadProgress) {
                            downloadProgress = progress;
                            if (bound) {
                                callback.updateDownloadProgress(downloadProgress);
                            }
                        }
                        if (downloadedSize == totalSize) {
                            setDownloadFinished();
                            return;
                        }
                    }
                    size = networkHelper.getDownloadProgress();
                }
            });
    }

    public void startDownload() {
        updateFile();
        executor.execute(() -> {
            if (!checkIfAlreadyDownloaded()) {
                downloadStarted = true;
                toast(R.string.status_downloading);
                if (bound) {
                    callback.setInitialProgress(downloadedSize, totalSize);
                }
                networkHelper.startDownload(file, network, downloadedSize);
            }
        });
    }

    private void restoreState() {
        Bundle bundle = new Bundle();
        if (buildInfo != null) {
            bundle.putBundle(Utils.BUILD_INFO, buildInfo.getBundle());
        }
        if (downloadStarted || downloadFinished) {
            bundle.putBoolean(Utils.DOWNLOAD_STARTED, downloadStarted);
            bundle.putBoolean(Utils.DOWNLOAD_PAUSED, downloadPaused);
            bundle.putBoolean(Utils.DOWNLOAD_FINISHED, downloadFinished);
            bundle.putLong(Utils.DOWNLOADED_SIZE, downloadedSize);
            bundle.putLong(Utils.BUILD_SIZE, totalSize);
        }
        bundle.putBoolean(Utils.LOCAL_UPGRADE_MODE, localUpgradeMode);
        if (updateStarted || updateFinished) {
            bundle.putBoolean(Utils.UPDATE_STARTED, updateStarted);
            bundle.putBoolean(Utils.UPDATE_PAUSED, updatePaused);
            bundle.putBoolean(Utils.UPDATE_FINISHED, updateFinished);
            bundle.putInt(Utils.UPDATE_EXIT_CODE, updateExitCode);
            bundle.putInt(Utils.UPDATE_STATUS, updateStatus);
            bundle.putInt(Utils.UPDATE_PROGRESS, updateProgress);
        }
        if (bound) {
            callback.restoreActivityState(bundle);
        }
    }

    private boolean checkIfAlreadyDownloaded() {
        if (file != null && file.exists()) {
            downloadedSize = file.length();
        } else {
            return false;
        }
        totalSize = buildInfo.getFileSize();
        if (downloadedSize == totalSize) {
            checkMd5sum();
            downloadFinished = true;
            restoreState();
            return true;
        } else {
            return false;
        }
    }

    private void checkMd5sum() {
        executor.execute(() -> {
            toast(R.string.checking_md5sum);
            boolean pass = false;
            if (file != null && file.exists()) {
                pass = buildInfo.checkMd5sum(file);
            } else {
                toast(R.string.file_not_found);
            }
            if (bound) {
                callback.md5sumCheckPassed(pass);
            }
            if (!pass) {
                deleteDownload();
            }
        });
    }

    private void updateFile() {
        File dir = new File(PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString(Utils.DOWNLOAD_LOCATION_KEY, Utils.DEFAULT_DOWNLOAD_LOC));
        if (!dir.exists() || (dir.exists() && !dir.isDirectory())) {
            dir.mkdirs();
        }
        file = new File(dir, buildInfo.getFileName());
    }

    public void pauseDownload(boolean pause) {
        downloadPaused = pause;
        if (downloadPaused) {
            networkHelper.cleanup();
        } else {
            startDownload();
        }
    }

    public void cancelDownload() {
        toast(R.string.status_download_cancelled);
        if (future != null) {
            future.cancel(true);
        }
        networkHelper.cleanup();
        resetDownloadStatus();
    }

    public void deleteDownload() {
        try {
            if (!file.isDirectory() && file.exists()) {
                file.delete();
            }
        } catch (SecurityException e) {
            toast(R.string.unable_to_delete);
            Utils.log(e);
        }
    }

    public void setFileForLocalUpgrade(String path) {
        localUpgradeMode = true;
        File tmp = new File(path);
        if (tmp.exists() && !tmp.isDirectory()) {
            file = tmp;
        }
    }

    private void setDownloadFinished() {
        downloadStarted = false;
        downloadFinished = true;
        if (bound) {
            callback.onFinishedDownload();
        }
        checkMd5sum();
    }

    private void waitAndDispatchMessage() {
        executor.execute(() -> {
            Instant start = Instant.now();
            Instant end;
            while (!isOnline) {
                Utils.sleepThread(500);
                end = Instant.now();
                if (Duration.between(start, end).toMillis() >= 5000) {
                    if (bound) {
                        callback.noInternet();
                    }
                    return;
                }
            }
        });
    }

    private void toast(int id) {
        handler.post(() ->
            Toast.makeText(this, getString(id), Toast.LENGTH_SHORT).show());
    }

    public void startUpdate() {
        if (updateEngine == null) {
            updateEngine = new UpdateEngine();
            updateEngine.setPerformanceMode(true);
        }
        updateEngine.bind(updateEngineCallback);
        if (file != null && file.exists()) {
            PayloadInfo payloadInfo = new PayloadInfo(file);
            if (!payloadInfo.validateData()) {
                resetUpdateStatus();
                updateExitCode = Utils.FILE_INVALID;
                toast(R.string.invalid_zip_file);
                if (bound) {
                    callback.onFinishedUpdate(updateExitCode);
                }
                return;
            }
            try {
                updateEngine.cleanupAppliedPayload();
                updateEngine.applyPayload(payloadInfo.getFilePath(),
                    payloadInfo.getOffset(), payloadInfo.getSize(), payloadInfo.getHeader());
            } catch (ServiceSpecificException e) {
                Utils.log(e);
                updateExitCode = Utils.APPLY_PAYLOAD_FAILED;
                if (bound) {
                    callback.onFinishedUpdate(updateExitCode);
                }
                resetUpdateStatus();
            }
        } else {
            Utils.log("update zip file is either null or does not exist");
        }
    }

    public void pauseUpdate(boolean paused) {
        updatePaused = paused;
        try {
            if (updatePaused) {
                updateEngine.suspend();
            } else {
                updateEngine.resume();
            }
        } catch(ServiceSpecificException e) {
            // Probably no on-going update
            Utils.log(e);
        }
    }

    public void cancelUpdate() {
        try {
            if (updateStarted) {
                updateEngine.cancel();
            }
        } catch (ServiceSpecificException e) {
            // Probably no update to cancel
            Utils.log(e);
        }
        resetUpdateStatus();
    }

    private void resetUpdateStatus() {
        updateStarted = updatePaused = updateFinished = false;
        updateStatus = updateExitCode = -1;
        updateProgress = 0;
        if (updateEngine != null) {
            updateEngine.resetStatus();
            updateEngine.cleanupAppliedPayload();
            updateEngine.unbind();
        }
        if (localUpgradeMode) {
            localUpgradeMode = false;
            file = null;
        }
    }

    private void resetDownloadStatus() {
        downloadStarted = downloadPaused = downloadFinished = false;
        totalSize = downloadedSize = downloadProgress = 0;
    }

    public final class ActivityBinder extends Binder {
        public UpdaterService getService() {
            return UpdaterService.this;
        }
    }
}
