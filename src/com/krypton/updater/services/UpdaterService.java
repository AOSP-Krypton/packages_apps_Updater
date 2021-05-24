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

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.os.FileUtils.S_IRWXU;
import static android.os.FileUtils.S_IRWXG;
import static android.os.PowerManager.REBOOT_REQUESTED_BY_DEVICE_OWNER;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.UpdateEngine.ErrorCodeConstants.*;
import static android.os.UpdateEngine.UpdateStatusConstants.*;
import static com.krypton.updater.util.Constants.*;
import static java.io.File.pathSeparator;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.ServiceSpecificException;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;

import com.google.common.io.Files;
import com.krypton.updater.callbacks.*;
import com.krypton.updater.util.*;
import com.krypton.updater.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.json.JSONException;

public class UpdaterService extends Service implements NetworkHelperCallbacks {

    private static final String TAG = "UpdaterService";
    private static final String WL_TAG ="UpdaterService.WakeLock";
    private static final String OTA_DIR = "kosp_ota";
    private static final String DOWNLOAD_DIR ="downloads";
    private static final File EXTERNAL_SD = Environment.getExternalStorageDirectory();
    private final IBinder activityBinder = new ActivityBinder();
    private final NetworkHelper networkHelper = new NetworkHelper();
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final UpdateEngine updateEngine = new UpdateEngine();
    private final File otaPackageDir = new File(Environment.getDataDirectory(), OTA_DIR);
    private final File downloadDir = new File(otaPackageDir, DOWNLOAD_DIR);
    private ActivityCallbacks callback;
    private ConnectivityManager connManager;
    private PowerManager powerManager;
    private WakeLock wakeLock;
    private Network network;
    private Future future;
    private BuildInfo buildInfo;
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
                case IDLE:
                case CLEANUP_PREVIOUS_UPDATE:
                    // We don't have to update the ui for these
                    break;
                case UPDATE_AVAILABLE:
                    if (!updateStarted) {
                        updateStarted = true;
                        if (bound) {
                            callback.onStartingUpdate();
                        }
                        acquireWakeLock();
                    }
                    break;
                case DOWNLOADING:
                case FINALIZING:
                    int tmpProgress = (int) (percent*100);
                    if (status != updateStatus || tmpProgress > updateProgress) {
                        updateStatus = status;
                        updateProgress = tmpProgress;
                        if (bound) {
                            callback.onStatusUpdate(updateStatus, updateProgress);
                        }
                    }
                    break;
                case UPDATED_NEED_REBOOT:
                    releaseWakeLock();
                    // Ready for reboot, onFinishedUpdate will enable the reboot button
                    break;
                default:
                    Utils.log("status", status);
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            updateStarted = false;
            // Only update status if user didn't order update cancellation
            if (errorCode != USER_CANCELLED) {
                updateExitCode = errorCode;
                if (updateExitCode == SUCCESS) {
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
        updateEngine.setPerformanceMode(true);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, WL_TAG);
        }
        new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND).start();
        networkHelper.setListener(this);
        connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        connManager.registerDefaultNetworkCallback(netCallback);
        resetUpdateStatus();
        resetDownloadStatus();
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
        releaseWakeLock();
        unregisterCallback();
        executor.shutdown();
        connManager.unregisterNetworkCallback(netCallback);
        updateEngine.unbind();
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
                    Utils.log(e);
                    if (bound) {
                        callback.fetchBuildInfoFailed();
                    }
                    return;
                }

                if (foundNew) {
                    if (!localUpgradeMode) {
                        updateFile(true, buildInfo.getFileName());
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
        executor.execute(() -> {
            if (!checkIfAlreadyDownloaded()) {
                downloadStarted = true;
                toast(R.string.downloading);
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
            bundle.putBundle(BUILD_INFO, buildInfo.getBundle());
        }
        if (downloadStarted || downloadFinished) {
            bundle.putBoolean(DOWNLOAD_STARTED, downloadStarted);
            bundle.putBoolean(DOWNLOAD_PAUSED, downloadPaused);
            bundle.putBoolean(DOWNLOAD_FINISHED, downloadFinished);
            bundle.putLong(DOWNLOADED_SIZE, downloadedSize);
            bundle.putLong(BUILD_SIZE, totalSize);
        }
        bundle.putBoolean(LOCAL_UPGRADE_MODE, localUpgradeMode);
        if (updateStarted || updateFinished) {
            bundle.putBoolean(UPDATE_STARTED, updateStarted);
            bundle.putBoolean(UPDATE_PAUSED, updatePaused);
            bundle.putBoolean(UPDATE_FINISHED, updateFinished);
            bundle.putInt(UPDATE_EXIT_CODE, updateExitCode);
            bundle.putInt(UPDATE_STATUS, updateStatus);
            bundle.putInt(UPDATE_PROGRESS, updateProgress);
        }
        if (bound) {
            callback.restoreActivityState(bundle);
        }
    }

    private boolean checkIfAlreadyDownloaded() {
        totalSize = buildInfo.getFileSize();
        if (file != null && file.exists()) {
            downloadedSize = file.length();
        }
        if (downloadedSize == totalSize) {
            if (checkMd5()) {
                downloadFinished = true;
                restoreState();
                return true;
            } else {
                downloadedSize = 0;
                return false;
            }
        }
        return false;
    }

    private boolean checkMd5() {
        Future<Boolean> checkMd5Future = executor.submit(() -> {
            toast(R.string.checking_md5);
            boolean pass = false;
            if (file != null && file.exists()) {
                pass = buildInfo.checkMd5(file);
            } else {
                toast(R.string.file_not_found);
            }
            if (pass) {
                if (bound) {
                    callback.onPrepareForUpdate();
                }
            } else {
                if (bound) {
                    callback.md5CheckFailed();
                }
                deleteDownload();
            }
            return new Boolean(pass);
        });
        try {
            return checkMd5Future.get().booleanValue();
        } catch (InterruptedException|ExecutionException e) {
            Utils.log(e);
        }
        return false;
    }

    private void updateFile(boolean toDownload, String name) {
        if (!otaPackageDir.isDirectory()) {
            throw new RuntimeException("ota package dir " +
                otaPackageDir.getAbsolutePath() + " does not exist");
        }
        if (!(otaPackageDir.canRead() &&
                otaPackageDir.canWrite() && otaPackageDir.canExecute())) {
            throw new RuntimeException("no rwx permission for " +
                otaPackageDir.getAbsolutePath());
        }
        if (!downloadDir.isDirectory()) {
            downloadDir.mkdirs();
            int errno = FileUtils.setPermissions(downloadDir,
                    S_IRWXU | S_IRWXG, -1, -1);
            if (errno != 0) {
                Utils.log("setPermissions for " +
                    downloadDir.getAbsolutePath() + " failed with errno ", errno);
            }
        }
        file = new File(toDownload ? downloadDir : otaPackageDir, name);
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
        toast(R.string.download_cancelled);
        if (future != null) {
            future.cancel(true);
        }
        networkHelper.cleanup();
        resetDownloadStatus();
    }

    public void deleteDownload() {
        if (file.isFile()) {
            if (!file.delete()) {
                toast(R.string.unable_to_delete);
            }
        }
    }

    public void setFileForLocalUpgrade(String path) {
        localUpgradeMode = true;
        File localFile = new File(EXTERNAL_SD, path);
        if (localFile.isFile()) {
            executor.execute(() -> {
                String fileName = path.substring(path.lastIndexOf(pathSeparator) + 1);
                updateFile(false, fileName);
                try {
                    if (!file.exists() || file.delete()) {
                        if (!file.createNewFile()) {
                            Utils.log("createNewFile failed, aborting");
                            if (bound) {
                                callback.onFinishedUpdate(FILE_EXCEPTION);
                            }
                        }
                        int errno = FileUtils.setPermissions(file,
                                S_IRWXU | S_IRWXG, -1, -1);
                        if (errno == 0) {
                            toast(R.string.copying);
                            Files.copy(localFile, file);
                            if (bound) {
                                callback.onPrepareForUpdate();
                            }
                        } else {
                            Utils.log("setPermissions for " +
                                file.getAbsolutePath() + "failed with errno ", errno);
                            if (bound) {
                                callback.onFinishedUpdate(FILE_EXCEPTION);
                            }
                        }
                    }
                } catch (IOException e) {
                    Utils.log(e);
                    if (bound) {
                        callback.onFinishedUpdate(FILE_EXCEPTION);
                    }
                }
            });
        } else {
            toast(R.string.file_not_found);
            if (bound) {
                callback.onFinishedUpdate(FILE_INVALID);
            }
        }
    }

    public void attemptExportToFolder(String path) {
        if (buildInfo != null && file != null &&
                downloadFinished && !updateStarted) {
            File folder = new File(EXTERNAL_SD, path);
            if (folder.isDirectory() && folder.canWrite()) {
                try {
                    toast(R.string.exporting);
                    Files.copy(file, new File(folder, buildInfo.getFileName()));
                    toast(R.string.export_finished);
                } catch (IOException e) {
                    Utils.log(e);
                    toast(R.string.export_failed);
                }
            }
        } else {
            toast(R.string.export_not_possible);
        }
    }

    private void setDownloadFinished() {
        downloadStarted = false;
        downloadFinished = true;
        if (bound) {
            callback.onFinishedDownload();
        }
        checkMd5();
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
        if (bound) {
            callback.toastOnUiThread(id);
        }
    }

    public void startUpdate() {
        updateEngine.bind(updateEngineCallback);
        if (file != null && file.exists()) {
            PayloadInfo payloadInfo = new PayloadInfo(file);
            if (!payloadInfo.validateData()) {
                resetUpdateStatus();
                updateExitCode = FILE_INVALID;
                toast(R.string.invalid_zip_file);
                if (bound) {
                    callback.onFinishedUpdate(updateExitCode);
                }
                return;
            }
            updateEngine.cleanupAppliedPayload();
            try {
                updateEngine.applyPayload(payloadInfo.getFilePath(),
                    payloadInfo.getOffset(), payloadInfo.getSize(), payloadInfo.getHeader());
            } catch (ServiceSpecificException e) {
                Utils.log(e);
                updateExitCode = APPLY_PAYLOAD_FAILED;
                if (bound) {
                    callback.onFinishedUpdate(updateExitCode);
                }
                resetUpdateStatus();
            }
        } else if (file == null) {
            Utils.log("update zip file is null");
        } else {
            Utils.log("update zip file does not exist");
        }
    }

    public void pauseUpdate(boolean paused) {
        updatePaused = paused;
        try {
            if (updatePaused) {
                releaseWakeLock();
                updateEngine.suspend();
            } else {
                acquireWakeLock();
                updateEngine.resume();
            }
        } catch(ServiceSpecificException e) {
            // No ongoing update to pause or resume, there is no need to log this
        }
    }

    public void cancelUpdate() {
        try {
            if (updateStarted) {
                releaseWakeLock();
                updateEngine.cancel();
            }
        } catch (ServiceSpecificException e) {
            // No ongoing update to cancel, there is no need to log this
        }
        resetUpdateStatus();
    }

    private void resetUpdateStatus() {
        updateStarted = updatePaused = updateFinished = false;
        updateStatus = updateExitCode = -1;
        updateProgress = 0;
        try {
            // Cancel is attempted here to cancel any ongoing updates that the service is not aware of
            updateEngine.cancel();
            updateEngine.resetStatus();
        } catch (ServiceSpecificException e) {
            // No ongoing update to cancel, there is no need to log this
        } finally {
            // Cleanup and unbind no matter what
            updateEngine.cleanupAppliedPayload();
            updateEngine.unbind();
        }
        if (localUpgradeMode) {
            localUpgradeMode = false;
        }
    }

    private void resetDownloadStatus() {
        downloadStarted = downloadPaused = downloadFinished = false;
        totalSize = downloadedSize = downloadProgress = 0;
    }

    public void rebootSystem() {
        if (powerManager != null) {
            powerManager.reboot(REBOOT_REQUESTED_BY_DEVICE_OWNER);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    public final class ActivityBinder extends Binder {
        public UpdaterService getService() {
            return UpdaterService.this;
        }
    }
}
