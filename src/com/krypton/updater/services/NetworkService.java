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
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.json.JSONException;

public class NetworkService extends Service implements NetworkInterface.Listener {

    private ServiceHandler serviceHandler;
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
    private boolean isInBackround = false;
    private boolean isOnline = false;
    private long totalSize = 0;
    private long downloadedSize = 0;
    private int downloadProgress = 0;

    @Override
    public void onCreate() {
        final HandlerThread thread = new HandlerThread("NetworkService",
            Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        serviceHandler = new ServiceHandler(thread.getLooper());
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
        if (intent != null) {
            if (intent.hasExtra("Handler")) {
                uiMessenger = intent.getParcelableExtra("Handler");
            } else {
                Message msg = serviceHandler.obtainMessage();
                msg.what = intent.getIntExtra(Utils.MESSAGE, -1);
                msg.arg1 = startId;
                serviceHandler.sendMessage(msg);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        connManager.unregisterNetworkCallback(netCallback);
    }

    private void updateBuildInfo() {
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
                } catch (IOException e) {
                    sendMessage(Utils.FAILED_TO_UPDATE_BUILD_INFO);
                    return;
                } catch (JSONException e) {
                    sendMessage(Utils.FAILED_TO_UPDATE_BUILD_INFO);
                    Utils.log(e);
                    return;
                }

                if (foundNew) {
                    totalSize = buildInfo.getFileSize();
                    sendMessage(Utils.UPDATED_BUILD_INFO, buildInfo.getBundle());
                    checkIfAlreadyDownloaded();
                } else {
                    sendMessage(Utils.NO_NEW_BUILD_FOUND);
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
                        sendMessage(Utils.UPDATE_DOWNLOADED_SIZE,
                            Utils.parseProgressText(downloadedSize, totalSize));
                        int progress = (int) ((downloadedSize*100)/totalSize);
                        if (progress > downloadProgress) {
                            downloadProgress = progress;
                            sendMessage(downloadProgress, Utils.UPDATE_PROGRESS_BAR);
                        }
                        if (downloadedSize == totalSize) {
                            setDownloadFinished();
                            return;
                        }
                    }
                    size = netInterface.getDownloadProgress();
                }
            });
    }

    private void sendMessage(int what) {
        sendMessage(what, null);
    }

    private void sendMessage(int arg1, int what) {
        sendMessage(arg1, -1, what, null, null);
    }

    private void sendMessage(int what, Object obj) {
        sendMessage(-1, -1, what, null, obj);
    }

    private void sendMessage(int what, Bundle bundle) {
        sendMessage(-1, -1, what, bundle, null);
    }

    private void sendMessage(int arg1, int arg2, int what, Bundle bundle, Object obj) {
        if (!isInBackround && uiMessenger != null) {
            try {
                Message msg = Message.obtain();
                msg.arg1 = arg1;
                msg.arg2 = arg2;
                msg.what = what;
                if (bundle != null) {
                    msg.setData(bundle);
                }
                if (obj != null) {
                    msg.obj = obj;
                }
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Utils.log("Target handler does not exist anymore");
            }
        }
    }

    private void startDownload() {
        executor.execute(() -> {
            checkIfAlreadyDownloaded();
            downloadStarted = true;
            serviceHandler.post(() -> toast(R.string.status_downloading));
            if (!netInterface.hasSetUrl()) {
                netInterface.setDownloadUrl();
            }
            Bundle bundle = new Bundle();
            bundle.putLong(Utils.DOWNLOADED_SIZE, downloadedSize);
            bundle.putLong(Utils.BUILD_SIZE, totalSize);
            sendMessage(Utils.SET_INITIAL_DOWNLOAD_PROGRESS, bundle);
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
        sendMessage(Utils.RESTORE_STATUS, bundle);
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
            stopSelf(startId);
        }
    }

    private void reset() {
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

    private void deleteDownload() {
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
        sendMessage(Utils.FINISHED_DOWNLOAD);
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
                    sendMessage(Utils.NO_INTERNET);
                    return;
                }
            }
        });
    }

    private void toast(int id) {
        Toast.makeText(this, getString(id), Toast.LENGTH_SHORT).show();
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Utils.FETCH_BUILD_INFO:
                    updateBuildInfo();
                    break;
                case Utils.APP_IN_BACKGROUND:
                    isInBackround = true;
                    break;
                case Utils.APP_IN_FOREGROUND:
                    isInBackround = false;
                    break;
                case Utils.START_DOWNLOAD:
                    startDownload();
                    break;
                case Utils.PAUSE_DOWNLOAD:
                    downloadPaused = true;
                    netInterface.cleanup();
                    break;
                case Utils.RESUME_DOWNLOAD:
                    downloadPaused = false;
                    startDownload();
                    break;
                case Utils.CANCEL_DOWNLOAD:
                    if (downloadStarted) {
                        reset();
                    }
                    break;
                case Utils.DELETE_DOWNLOAD:
                    deleteDownload();
                    break;
                case Utils.NO_INTERNET:
                    NetworkService.this.sendMessage(Utils.NO_INTERNET);
                    break;
                default:
                    Utils.log("Unknown message", msg.what);
            }
        }
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
