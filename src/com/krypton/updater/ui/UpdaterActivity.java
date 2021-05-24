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

package com.krypton.updater.ui;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.POWER_SERVICE;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.graphics.Color.TRANSPARENT;
import static android.os.UpdateEngine.ErrorCodeConstants.*;
import static android.os.UpdateEngine.UpdateStatusConstants.*;
import static android.view.HapticFeedbackConstants.KEYBOARD_PRESS;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.krypton.updater.util.Constants.*;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable2.AnimationCallback;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GestureDetectorCompat;

import com.krypton.updater.callbacks.ActivityCallbacks;
import com.krypton.updater.util.BuildInfo;
import com.krypton.updater.R;
import com.krypton.updater.services.UpdaterService;
import com.krypton.updater.services.UpdaterService.ActivityBinder;
import com.krypton.updater.util.Utils;

public class UpdaterActivity extends AppCompatActivity implements ActivityCallbacks {

    private static final int SELECT_FILE = 1001;
    private static final int SELECT_EXPORT_FOLDER = 1002;
    private UpdaterService updaterService;
    private GestureDetectorCompat detector;
    private LinearLayout downloadProgressLayout, updateProgressLayout;
    private AnimatedVectorDrawable animatedDrawable;
    private ProgressBar downloadProgressBar, updateProgressBar;
    private TextView downloadProgressText, downloadStatus;
    private TextView updateStatus, updateStep;
    private TextView viewLatestBuild, latestBuildVersion,
        latestBuildTimestamp, latestBuildName, latestBuildMd5;
    private TextView postUpdateInfo;
    private ImageView refreshIcon, infoIcon;
    private Button downloadButton, pauseButton,
        cancelButton, exportButton;
    private Button localUpgradeButton, updateButton,
        pauseUpdateButton, cancelUpdateButton, rebootButton;
    private SharedPreferences sharedPrefs;
    private boolean bound = false;
    private boolean hasUpdatedBuildInfo = true;
    private boolean downloadAvailable = false;
    private boolean downloadStarted = false;
    private boolean downloadPaused = false;
    private boolean downloadFinished = false;
    private boolean updateStarted = false;
    private boolean updatePaused = false;
    private boolean localUpgradeMode = false;
    private int updateStepNum = 0;
    private Uri fileUri, exportFolderUri;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            if (!bound) {
                updaterService = ((ActivityBinder) binder).getService();
                updaterService.registerCallback(UpdaterActivity.this);
                bound = true;
                if (fileUri != null) {
                    localUpgradeMode = true;
                    updaterService.setFileForLocalUpgrade(getPathFromUri(fileUri));
                    runOnUiThread(() ->
                        setVisibile(false, localUpgradeButton, downloadButton));
                }
                if (exportFolderUri != null) {
                    updaterService.attemptExportToFolder(getPathFromUri(exportFolderUri));
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Utils.log("service connection lost");
        }
    };

    private SimpleOnGestureListener gestureListener = new SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            float diffX = event2.getRawX() - event1.getRawX();
            float diffY = event2.getRawY() - event1.getRawY();
            if (Math.abs(diffX) < 100 && diffY > 300 && !downloadStarted && !downloadFinished) {
                viewLatestBuild.setText(getString(R.string.fetching_build_status_text));
                updateBuildInfo();
            }
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, UpdaterService.class));
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setAppTheme(sharedPrefs.getInt(SettingsFragment.THEME_KEY, 2));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.updater_app_title);
        setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.updater_activity);
        detector = new GestureDetectorCompat(this, gestureListener);
        setWidgets();
        setCurrentBuildInfo();
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(this, UpdaterService.class),
            connection, BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        fileUri = exportFolderUri = null;
        if (bound) {
            updaterService.unregisterCallback();
            unbindService(connection);
            bound = false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent resultData) {
        if (resultCode == RESULT_OK && resultData != null) {
            if (requestCode == SELECT_FILE) {
                fileUri = resultData.getData();
            } else if (requestCode == SELECT_EXPORT_FOLDER) {
                exportFolderUri = resultData.getData();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        detector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.option_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void restoreActivityState(Bundle bundle) {
        downloadStarted = bundle.getBoolean(DOWNLOAD_STARTED);
        downloadFinished = bundle.getBoolean(DOWNLOAD_FINISHED);
        downloadPaused = bundle.getBoolean(DOWNLOAD_PAUSED);
        runOnUiThread(() -> setNewBuildInfo(bundle.getBundle(BUILD_INFO)));
        if (downloadStarted || downloadFinished) {
            runOnUiThread(() -> {
                setDownloadLayout(bundle.getLong(DOWNLOADED_SIZE),
                    bundle.getLong(BUILD_SIZE));
                localUpgradeButton.setVisibility(GONE);
            });
        }
        localUpgradeMode = bundle.getBoolean(LOCAL_UPGRADE_MODE);
        if (localUpgradeMode) {
            runOnUiThread(() -> setVisibile(false, localUpgradeButton, downloadButton));
        }
        updateStarted = bundle.getBoolean(UPDATE_STARTED);
        boolean updateFinished = bundle.getBoolean(UPDATE_FINISHED);
        if (updateStarted || updateFinished) {
            runOnUiThread(() -> updateProgressLayout.setVisibility(VISIBLE));
            int updateStatusCode = bundle.getInt(UPDATE_STATUS);
            onStatusUpdate(updateStatusCode,
                bundle.getInt(UPDATE_PROGRESS));
            if (updateStatusCode == DOWNLOADING) {
                changeUpdateStatus(1);
            } else if (updateStatusCode == FINALIZING) {
                changeUpdateStatus(2);
            } else {
                runOnUiThread(() -> updateStatus.setText(getString(R.string.update_failed)));
            }
        }
        if (updateStarted) {
            updatePaused = bundle.getBoolean(UPDATE_PAUSED);
            runOnUiThread(() -> {
                pauseUpdateButton.setText(getString(updatePaused ? R.string.resume : R.string.pause));
                setUpdateStatusText();
                setVisibile(true, pauseUpdateButton, cancelUpdateButton);
            });
        } else if (updateFinished) {
            onFinishedUpdate(bundle.getInt(UPDATE_EXIT_CODE));
        }
    }

    @Override
    public void onFetchedBuildInfo(Bundle bundle) {
        downloadAvailable = true;
        runOnUiThread(() -> {
            setNewBuildInfo(bundle);
            if (!updateStarted && !localUpgradeMode) {
                downloadButton.setVisibility(VISIBLE);
            }
        });
    }

    @Override
    public void fetchBuildInfoFailed() {
        runOnUiThread(() -> setBuildFetchResult(R.string.unable_to_fetch_details));
    }

    @Override
    public void noUpdates() {
        runOnUiThread(() -> {
            setVisibile(false, latestBuildVersion,
                latestBuildTimestamp, latestBuildName, latestBuildMd5);
            setBuildFetchResult(R.string.current_is_latest);
        });
    }

    @Override
    public void noInternet() {
        downloadPaused = true;
        toastOnUiThread(R.string.no_internet);
        runOnUiThread(() -> {
            downloadStatus.setText(getString(R.string.no_internet_download_paused));
            pauseButton.setText(getString(R.string.resume));
        });
    }

    @Override
    public void setInitialProgress(long downloaded, long total) {
        runOnUiThread(() -> setDownloadLayout(downloaded, total));
    }

    @Override
    public void updateDownloadedSize(long downloaded, long total) {
        runOnUiThread(() ->
            downloadProgressText.setText(String.format("%d/%d MB",
                (int) downloaded/MB, (int) total/MB)));
    }

    @Override
    public void updateDownloadProgress(int progress) {
        runOnUiThread(() -> downloadProgressBar.setProgress(progress));
    }

    @Override
    public void onFinishedDownload() {
        downloadStarted = false;
        downloadFinished = true;
        runOnUiThread(() -> {
            downloadStatus.setText(getString(R.string.download_finished));
            setVisibile(false, downloadButton, pauseButton, cancelButton);
        });
    }

    @Override
    public void md5CheckFailed() {
        downloadFinished = false;
        toastOnUiThread(R.string.md5_check_failed);
        resetDownloadLayout();
    }

    @Override
    public void onPrepareForUpdate() {
        runOnUiThread(() -> showUpdateAndExportButton());
    }

    @Override
    public void onStartingUpdate() {
        runOnUiThread(() -> setVisibile(true,
            updateProgressLayout, pauseUpdateButton, cancelUpdateButton));
    }

    @Override
    public void onStatusUpdate(int status, int percent) {
        if (status == DOWNLOADING) {
            if (percent == 0) {
                changeUpdateStatus(1);
            }
            runOnUiThread(() -> updateProgressBar.setProgress(percent));
        } else if (status == FINALIZING) {
            if (percent == 0) {
                changeUpdateStatus(2);
            }
            runOnUiThread(() -> updateProgressBar.setProgress(percent));
        }
    }

    @Override
    public void onFinishedUpdate(int errorCode) {
        switch (errorCode) {
            case SUCCESS:
                runOnUiThread(() -> {
                    updateStatus.setText(R.string.update_finished);
                    setVisibile(true, infoIcon, postUpdateInfo, rebootButton);
                    setVisibile(false, updateButton, pauseUpdateButton, cancelUpdateButton);
                });
                break;
            case APPLY_PAYLOAD_FAILED:
            case FILE_INVALID:
            case FILE_EXCEPTION:
                toastOnUiThread(R.string.update_failed);
                resetUpdateLayout();
                break;
            case DOWNLOAD_INVALID_METADATA_MAGIC_STRING:
            case DOWNLOAD_METADATA_SIGNATURE_MISMATCH:
                toastOnUiThread(R.string.metadata_verification_failed);
                resetUpdateLayout();
                break;
            case PAYLOAD_TIMESTAMP_ERROR:
                toastOnUiThread(R.string.attempting_downgrade);
                resetUpdateLayout();
                break;
            case DOWNLOAD_TRANSFER_ERROR:
                runOnUiThread(() -> {
                    updateStatus.setText(R.string.payload_transfer_error_retry);
                    setVisibile(false, pauseUpdateButton, cancelUpdateButton);
                    showUpdateAndExportButton();
                });
                break;
            default:
                Utils.log("errorCode", errorCode);
                runOnUiThread(() -> {
                    updateStatus.setText(R.string.update_failed);
                    setVisibile(false, pauseUpdateButton, cancelUpdateButton);
                    showUpdateAndExportButton();
                });
        }
    }

    @Override
    public void toastOnUiThread(int id) {
        runOnUiThread(() ->
            Toast.makeText(this, getString(id), Toast.LENGTH_SHORT).show());
    }

    private void setUpdateStatusText() {
        if (updatePaused) {
            updateStatus.setText(getString(R.string.update_paused));
        } else {
            updateStatus.setText(getString(updateStepNum == 1 ? R.string.update_downloading : R.string.updating));
        }
    }

    private void changeUpdateStatus(int step) {
        updateStepNum = step;
        runOnUiThread(() -> {
            updateStatus.setText(getString(updateStepNum == 1 ?
                R.string.update_downloading : R.string.updating));
            updateStep.setText(String.format("%s %d/%d",
                getString(R.string.step), updateStepNum, 2));
        });
    }

    protected static void setAppTheme(int mode) {
        switch (mode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void setCurrentBuildInfo() {
        ((TextView) findViewById(R.id.view_device))
            .setText(getString(R.string.device, Utils.getDevice()));
        ((TextView) findViewById(R.id.view_version))
            .setText(getString(R.string.version, Utils.getVersion()));
        ((TextView) findViewById(R.id.view_timestamp))
            .setText(getString(R.string.date, Utils.getBuildDate()));
        viewLatestBuild.setText(getString(R.string.swipe_down_to_update));
    }

    private void setWidgets() {
        refreshIcon = (ImageView) findViewById(R.id.refresh_icon);
        infoIcon = (ImageView) findViewById(R.id.info_icon);
        animatedDrawable = (AnimatedVectorDrawable) refreshIcon.getDrawable();
        animatedDrawable.registerAnimationCallback(new AnimationCallback() {
            @Override
            public void onAnimationEnd(Drawable drawable) {
                if (!hasUpdatedBuildInfo) {
                    ((AnimatedVectorDrawable) drawable).start();
                } else {
                    runOnUiThread(() -> refreshIcon.setVisibility(GONE));
                }
            }
        });
        viewLatestBuild = (TextView) findViewById(R.id.view_latest_build);
        latestBuildVersion = (TextView) findViewById(R.id.view_latest_build_version);
        latestBuildTimestamp = (TextView) findViewById(R.id.view_latest_build_timestamp);
        latestBuildName = (TextView) findViewById(R.id.view_latest_build_filename);
        latestBuildMd5 = (TextView) findViewById(R.id.view_latest_build_md5);

        downloadButton = (Button) findViewById(R.id.download_button);
        pauseButton = (Button) findViewById(R.id.pause_resume_button);
        cancelButton = (Button) findViewById(R.id.cancel_button);
        exportButton = (Button) findViewById(R.id.export_button);
        downloadProgressLayout = (LinearLayout) findViewById(R.id.download_progress_layout);
        updateProgressLayout = (LinearLayout) findViewById(R.id.update_progress_layout);
        downloadStatus = (TextView) findViewById(R.id.download_status);
        updateStatus = (TextView) findViewById(R.id.update_status);
        updateStep = (TextView) findViewById(R.id.update_step);
        downloadProgressBar = (ProgressBar) findViewById(R.id.download_progress);
        updateProgressBar = (ProgressBar) findViewById(R.id.update_progress);
        downloadProgressText = (TextView) findViewById(R.id.numeric_download_progress);

        localUpgradeButton = (Button) findViewById(R.id.local_upgrade_button);
        updateButton = (Button) findViewById(R.id.update_button);
        pauseUpdateButton = (Button) findViewById(R.id.pause_update_button);
        cancelUpdateButton = (Button) findViewById(R.id.cancel_update_button);
        rebootButton = (Button) findViewById(R.id.reboot_button);

        postUpdateInfo = (TextView) findViewById(R.id.post_update_info);
    }

    private void updateBuildInfo() {
        if (hasUpdatedBuildInfo) {
            hasUpdatedBuildInfo = false;
        } else {
            return;
        }
        refreshIcon.setVisibility(VISIBLE);
        animatedDrawable.start();
        if (bound) {
            updaterService.updateBuildInfo();
        }
    }

    private void setBuildFetchResult(int textId) {
        hasUpdatedBuildInfo = true;
        viewLatestBuild.setText(getString(textId));
    }

    private void setNewBuildInfo(Bundle bundle) {
        if (bundle != null) {
            runOnUiThread(() -> {
                setBuildFetchResult(R.string.new_update);

                latestBuildVersion.setText(getString(R.string.version,
                    bundle.getString(BUILD_VERSION)));
                latestBuildTimestamp.setText(getString(R.string.date,
                    bundle.getString(BUILD_DATE)));
                latestBuildName.setText(getString(R.string.file,
                    bundle.getString(BUILD_NAME)));
                latestBuildMd5.setText(getString(R.string.md5,
                    bundle.getString(BUILD_MD5SUM)));

                setVisibile(true, latestBuildVersion,
                    latestBuildTimestamp, latestBuildName, latestBuildMd5);
            });
        }
    }

    private String getString(int id, String str) {
        return getString(id) + " " + str;
    }

    private void setDownloadLayout(long downloaded, long total) {
        downloadButton.setVisibility(GONE);
        updateDownloadedSize(downloaded, total);
        updateDownloadProgress((int) ((downloaded*100)/total));
        if (downloadStarted) {
            downloadStatus.setText(getString(downloadPaused ?
                R.string.download_paused : R.string.downloading));
        } else if (downloadFinished) {
            downloadStatus.setText(getString(R.string.download_finished));
        }
        downloadProgressLayout.setVisibility(VISIBLE);
        if (downloadStarted) {
            pauseButton.setText(getString(downloadPaused ? R.string.resume : R.string.pause));
            setVisibile(true, pauseButton, cancelButton);
        }
    }

    private void setVisibile(boolean visible, View... views) {
        for (View v: views) {
            v.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private void resetDownloadLayout() {
        runOnUiThread(() -> {
            downloadProgressLayout.setVisibility(GONE);
            setVisibile(true, localUpgradeButton, downloadButton);
        });
    }

    private void resetUpdateLayout() {
        runOnUiThread(() -> {
            setVisibile(false, updateProgressLayout, pauseUpdateButton, cancelUpdateButton);
            localUpgradeButton.setVisibility(VISIBLE);
            if (downloadAvailable) {
                downloadButton.setVisibility(VISIBLE);
            }
        });
    }

    public void startUpdate(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        v.setVisibility(GONE);
        updateStarted = true;
        if (bound) {
            updaterService.startUpdate();
        }
    }

    public void pauseUpdate(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        updatePaused = !updatePaused;
        if (bound) {
            updaterService.pauseUpdate(updatePaused);
        }
        pauseUpdateButton.setText(getString(updatePaused ? R.string.resume : R.string.pause));
        setUpdateStatusText();
    }

    public void cancelUpdate(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        updateStarted = false;
        if (localUpgradeMode) {
            localUpgradeMode = false;
        }
        resetUpdateLayout();
        if (bound) {
            updaterService.cancelUpdate();
        }
    }

    public void startDownload(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        runOnUiThread(() -> setVisibile(false, v, localUpgradeButton));
        v.setVisibility(GONE);
        downloadStarted = true;
        downloadFinished = false;
        if (bound) {
            updaterService.startDownload();
        }
    }

    public void pauseOrResumeDownload(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        downloadPaused = !downloadPaused;
        downloadStatus.setText(getString(downloadPaused ?
            R.string.download_paused : R.string.downloading));
        pauseButton.setText(getString(downloadPaused ? R.string.resume : R.string.pause));
        if (bound) {
            updaterService.pauseDownload(downloadPaused);
        }
    }

    public void cancelDownload(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        downloadStarted = false;
        runOnUiThread(() -> {
            setVisibile(false, v, pauseButton, downloadProgressLayout);
            setVisibile(true, downloadButton, localUpgradeButton);
        });
        if (bound) {
            updaterService.cancelDownload();
        }
        showDeleteDialog();
    }

    public void exportFile(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        startActivityForResult(new Intent(
            ACTION_OPEN_DOCUMENT_TREE), SELECT_EXPORT_FOLDER);
    }

    public void rebootSystem(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        if (bound) {
            updaterService.rebootSystem();
        }
    }

    public void localUpgrade(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
        intent.addCategory(CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, SELECT_FILE);
    }

    private void showDeleteDialog() {
        AlertDialog confirmDeleteDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.delete_file)
            .setCancelable(false)
            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    if (bound) {
                        updaterService.deleteDownload();
                    }
                })
            .setNegativeButton(android.R.string.no, (dialog, which) ->
                    dialog.dismiss())
            .create();
        confirmDeleteDialog.getWindow().setBackgroundDrawable(new ColorDrawable(TRANSPARENT));
        confirmDeleteDialog.show();
    }

    private String getPathFromUri(Uri uri) {
        return uri.getLastPathSegment().replace("primary:", "");
    }

    private void showUpdateAndExportButton() {
        updateButton.setVisibility(VISIBLE);
        if (downloadFinished) {
            exportButton.setVisibility(VISIBLE);
        }
    }
}
