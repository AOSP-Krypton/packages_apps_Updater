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
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.krypton.updater.R;
import com.krypton.updater.BuildInfo;
import com.krypton.updater.NetworkInterface;
import com.krypton.updater.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.json.JSONException;

public class NetworkService extends Service implements NetworkInterface.Listener {

    private Handler handler;
    private IBinder networkBinder = new NetworkBinder();
    private ActivityCallbacks callback;
    private NetworkInterface netInterface;
    private ConnectivityManager connManager;
    private NetCallback netCallback;
    private Network network;
    private ExecutorService executor;
    private Future future;
    private Messenger uiMessenger;
    private BuildInfo buildInfo;
    private String downloadLocation;
    private File file;
    private int startId;
    private boolean downloadStarted = false;
    private boolean downloadPaused = false;
    private boolean downloadFinished = false;
    private boolean isOnline = false;
    private long totalSize = 0;
    private long downloadedSize = 0;
    private int downloadProgress = 0;

    @Override
    public void onCreate() {
        final HandlerThread thread = new HandlerThread("NetworkService",
            Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());
        netInterface = new NetworkInterface();
        netInterface.setListener(this);
        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netCallback = new NetCallback();
        connManager.registerDefaultNetworkCallback(netCallback);
        executor = Executors.newFixedThreadPool(4);
        downloadLocation = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(Utils.DOWNLOAD_LOCATION_KEY, Utils.DEFAULT_DOWNLOAD_LOC);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.startId = startId;
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return networkBinder;
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        connManager.unregisterNetworkCallback(netCallback);
    }

    public void registerCallback(ActivityCallbacks callback) {
        this.callback = callback;
    }

    public void unregisterCallback() {
        callback = null;
    }

    public void updateBuildInfo() {
        executor.execute(() -> {
            if (downloadStarted || downloadFinished) {
                restoreState();
            } else {
                boolean foundNew = false;
                try {
                    BuildInfo tmp = netInterface.fetchBuildInfo();
                    if (tmp != null) {
                        buildInfo = tmp;
                        foundNew = true;
                    }
                } catch (JSONException|IOException e) {
                    if (callback != null) {
                        callback.fetchBuildInfoFailed();
                    }
                    return;
                }

                if (foundNew) {
                    totalSize = buildInfo.getFileSize();
                    if (callback != null) {
                        callback.onFetchedBuildInfo(buildInfo.getBundle());
                    }
                    checkIfAlreadyDownloaded();
                } else {
                    if (callback != null) {
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
                long size = netInterface.getDownloadProgress();
                while (size != -1) {
                    if (size > downloadedSize) {
                        downloadedSize = size;
                        if (callback != null) {
                            callback.updateDownloadedSize(
                                Utils.parseProgressText(downloadedSize, totalSize));
                        }
                        int progress = (int) ((downloadedSize*100)/totalSize);
                        if (progress > downloadProgress) {
                            downloadProgress = progress;
                            if (callback != null) {
                                callback.updateDownloadProgress(downloadProgress);
                            }
                        }
                        if (downloadedSize == totalSize) {
                            setDownloadFinished();
                            checkMd5sum();
                            return;
                        }
                    }
                    size = netInterface.getDownloadProgress();
                }
            });
    }

    public void startDownload() {
        executor.execute(() -> {
            checkIfAlreadyDownloaded();
            downloadStarted = true;
            toast(R.string.status_downloading);
            if (!netInterface.hasSetUrl()) {
                netInterface.setDownloadUrl();
            }
            if (callback != null) {
                callback.setInitialProgress(downloadedSize, totalSize);
            }
            netInterface.startDownload(file, network, downloadedSize);
        });
    }

    private void restoreState() {
        Bundle bundle = new Bundle();
        bundle.putBundle(Utils.BUILD_INFO, buildInfo.getBundle());
        if (downloadStarted) {
            bundle.putBoolean(Utils.DOWNLOAD_PAUSED, downloadPaused);
        } else {
            bundle.putBoolean(Utils.DOWNLOAD_FINISHED, downloadFinished);
        }
        bundle.putLong(Utils.DOWNLOADED_SIZE, downloadedSize);
        bundle.putLong(Utils.BUILD_SIZE, totalSize);
        if (callback != null) {
            callback.restoreActivityState(bundle);
        }
    }

    private void checkIfAlreadyDownloaded() {
        File dir = new File(downloadLocation);
        if (!dir.exists() || (dir.exists() && !dir.isDirectory())) {
            dir.mkdirs();
        }
        file = new File(dir, buildInfo.getFileName());
        if (file.exists()) {
            downloadedSize = file.length();
        }
        if (downloadedSize == totalSize) {
            downloadFinished = true;
            restoreState();
            checkMd5sum();
            stopSelf(startId);
        }
    }

    private void checkMd5sum() {
        executor.execute(() -> {
            try {
                byte[] buffer = new byte[1048576];
                int bytesRead = 0;
                MessageDigest md5Digest = MessageDigest.getInstance("MD5");
                FileInputStream inStream = new FileInputStream(file);
                while ((bytesRead = inStream.read(buffer)) != -1) {
                    md5Digest.update(buffer, 0, bytesRead);
                }
                inStream.close();

                StringBuilder sb = new StringBuilder();
                for (byte b: md5Digest.digest()) {
                    sb.append(String.format("%02x", b));
                }
                boolean pass = sb.toString().equals(buildInfo.getMd5sum());
                if (callback != null) {
                    callback.md5sumCheckPassed(pass);
                }
                if (!pass) {
                    deleteDownload();
                }
            } catch (NoSuchAlgorithmException e) {
                // I am pretty sure MD5 exists
            } catch (FileNotFoundException e) {
                toast(R.string.file_not_found);
            } catch (IOException e) {
                // Do nothing
            }
        });
    }

    public void pauseDownload(boolean pause) {
        downloadPaused = pause;
        if (downloadPaused) {
            netInterface.cleanup();
        } else {
            startDownload();
        }
    }

    public void cancelDownload() {
        toast(R.string.status_download_cancelled);
        downloadStarted = false;
        downloadPaused = false;
        downloadFinished = false;
        if (future != null) {
            future.cancel(true);
        }
        netInterface.cleanup();
        downloadedSize = 0;
        downloadProgress = 0;
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

    private void setDownloadFinished() {
        downloadStarted = false;
        downloadFinished = true;
        if (callback != null) {
            callback.onFinishedDownload();
        }
        checkMd5sum();
        stopSelf(startId);
    }

    private void waitAndDispatchMessage() {
        executor.execute(() -> {
            Instant start = Instant.now();
            Instant end;
            while (!isOnline) {
                Utils.sleepThread(500);
                end = Instant.now();
                if (Duration.between(start, end).toMillis() >= 5000) {
                    if (callback != null) {
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

    public final class NetworkBinder extends Binder {

        public NetworkService getService() {
            return NetworkService.this;
        }

    }

    public static interface ActivityCallbacks {
        public void restoreActivityState(Bundle bundle);
        public void onFetchedBuildInfo(Bundle bundle);
        public void fetchBuildInfoFailed();
        public void noUpdates();
        public void noInternet();
        public void setInitialProgress(long downloaded, long total);
        public void updateDownloadedSize(String progressText);
        public void updateDownloadProgress(int progress);
        public void onFinishedDownload();
        public void md5sumCheckPassed(boolean passed);
    }

    private final class NetCallback extends ConnectivityManager.NetworkCallback {

        @Override
        public void onAvailable(Network network) {
            isOnline = true;
            NetworkService.this.network = network;
            if (downloadStarted && !downloadPaused) {
                if (future != null) {
                    future.cancel(true);
                }
                netInterface.cleanup();
                startDownload();
            }
        }

        @Override
        public void onLost(Network network) {
            isOnline = false;
            if (!downloadFinished) {
                waitAndDispatchMessage();
            }
            NetworkService.this.network = null;
            if (future != null) {
                future.cancel(true);
            }
            netInterface.cleanup();
        }
    }
}
