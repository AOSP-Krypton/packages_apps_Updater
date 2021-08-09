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

package com.krypton.updater.model.data;;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.UpdateEngine.ErrorCodeConstants.*;
import static android.os.UpdateEngine.UpdateStatusConstants.*;
import static com.krypton.updater.util.Constants.INDETERMINATE;
import static com.krypton.updater.util.Constants.UPDATE_PENDING;
import static com.krypton.updater.util.Constants.UPDATING;
import static com.krypton.updater.util.Constants.REBOOT_PENDING;
import static com.krypton.updater.util.Constants.FINISHED;
import static com.krypton.updater.util.Constants.PAUSED;
import static com.krypton.updater.util.Constants.CANCELLED;
import static com.krypton.updater.util.Constants.FAILED;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ServiceSpecificException;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.krypton.updater.model.data.DataStore;
import com.krypton.updater.R;
import com.krypton.updater.util.NotificationHelper;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private final OTAFileManager ofm;
    private final UpdateEngine updateEngine;
    private final NotificationHelper helper;
    private final DataStore dataStore;
    private final BehaviorProcessor<UpdateStatus> updateStatusProcessor;
    private HandlerThread thread;
    private Handler handler;
    private UpdateStatus updateStatus;

    private final UpdateEngineCallback updateEngineCallback = new UpdateEngineCallback() {
        @Override
        public void onStatusUpdate(int status, float percent) {
            if (status == DOWNLOADING || status == FINALIZING) {
                if (getCurrentStatusCode() != UPDATING) {
                    updateStatus.setStatusCode(UPDATING);
                }
                updateStatusProcessor.onNext(updateStatus.setStep(
                    status == DOWNLOADING ? 1 : 2));
            }
            switch (status) {
                case IDLE:
                case CLEANUP_PREVIOUS_UPDATE:
                    // We don't have to update the ui for these
                    break;
                case UPDATE_AVAILABLE:
                    setGlobalStatus(UPDATING);
                    break;
                case DOWNLOADING:
                case FINALIZING:
                    updateStatusProcessor.onNext(updateStatus.setProgress((int) (percent*100)));
                    break;
                case UPDATED_NEED_REBOOT:
                    // Ready for reboot
                    setGlobalStatus(REBOOT_PENDING);
                    break;
                default:
                    // Log unhandled cases
                    Log.e(TAG, "onStatusUpdate: unknown status code " + status);
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            switch (errorCode) {
                case SUCCESS:
                    updateStatusProcessor.onNext(updateStatus.setStatusCode(FINISHED));
                    break;
                case DOWNLOAD_INVALID_METADATA_MAGIC_STRING:
                case DOWNLOAD_METADATA_SIGNATURE_MISMATCH:
                    resetAndNotify(R.string.metadata_verification_failed);
                    break;
                case PAYLOAD_TIMESTAMP_ERROR:
                    resetAndNotify(R.string.attempting_downgrade);
                    break;
                case NEW_ROOTFS_VERIFICATION_ERROR:
                    resetAndNotify(R.string.rootfs_verification_failed);
                    break;
                case DOWNLOAD_TRANSFER_ERROR:
                    resetAndNotify(R.string.ota_transfer_error);
                    break;
                case USER_CANCELLED:
                    break;
                default:
                    // Log unhandled cases
                    Log.e(TAG, "onPayloadApplicationComplete: unknown errorCode " + errorCode);
            }
            reset();
        }
    };

    @Inject
    public UpdateManager(UpdateEngine updateEngine, OTAFileManager ofm,
            NotificationHelper helper, DataStore dataStore) {
        this.updateEngine = updateEngine;
        this.ofm = ofm;
        this.helper = helper;
        this.dataStore = dataStore;
        thread = new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND);
        updateStatus = new UpdateStatus();
        updateStatusProcessor = BehaviorProcessor.create();
        updateEngineReset(); // Cancel any ongoing updates / unbind callbacks we are not aware of
    }

    @WorkerThread
    public void start() {
        reset(); // Reset update engine whenever a new update is applied
        if (thread.getState() == Thread.State.TERMINATED) {
            // Create a new thread if the current one is terminated
            thread = new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND);
        }
        thread.start();
        handler = new Handler(thread.getLooper());
        updateEngine.setPerformanceMode(true);
        final PayloadInfo payloadInfo = new PayloadInfo();
        payloadInfo.extractPayloadInfo(ofm.getOTAFileUri());
        if (!payloadInfo.validateData()) {
            resetAndNotify(R.string.invalid_zip_file);
            return;
        }
        updateStatusProcessor.onNext(updateStatus.setStatusCode(INDETERMINATE));
        updateEngine.bind(updateEngineCallback, handler);
        try {
            updateEngine.applyPayload(payloadInfo.getFilePath(),
                payloadInfo.getOffset(), payloadInfo.getSize(), payloadInfo.getHeader());
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "ServiceSpecificException when applying payload", e);
            resetAndNotify(R.string.update_failed);
        }
    }

    public void pause(boolean pause) {
        try {
            if (pause) {
                updateEngine.suspend();
                updateStatusProcessor.onNext(updateStatus.setStatusCode(PAUSED));
            } else {
                updateEngine.resume();
                updateStatusProcessor.onNext(updateStatus.setStatusCode(INDETERMINATE));
            }
        } catch (ServiceSpecificException e) {
            // No ongoing update to suspend or resume, there is no need to log this
        }
    }

    @WorkerThread
    public void cancel() {
        updateEngineReset();
        updateStatusProcessor.onNext(updateStatus.setStatusCode(CANCELLED));
        thread.quitSafely();
    }

    public BehaviorProcessor<UpdateStatus> getUpdateStatusProcessor() {
        return updateStatusProcessor;
    }

    public int getCurrentStatusCode() {
        return updateStatus.getStatusCode();
    }

    public boolean isUpdating() {
        final int statusCode = getCurrentStatusCode();
        return statusCode >= INDETERMINATE && statusCode <= PAUSED;
    }

    public void userInitiatedReset() {
        reset();
        updateStatus = new UpdateStatus();
        updateStatusProcessor.onNext(updateStatus);
    }

    private void setGlobalStatus(int status) {
        handler.post(() -> dataStore.setGlobalStatus(status));
    }

    private void updateEngineReset() {
        try {
            updateEngine.cancel();
        } catch (ServiceSpecificException e) {
            // No ongoing update to cancel, there is no need to log this
        } finally {
            // Reset, cleanup and unbind
            reset();
        }
    }

    private void reset() {
        updateEngine.cleanupAppliedPayload();
        updateEngine.resetStatus();
        updateEngine.unbind();
    }

    private void resetAndNotify(int msgId) {
        updateStatusProcessor.onNext(updateStatus.setStatusCode(FAILED));
        setGlobalStatus(UPDATE_PENDING);
        reset();
        helper.notifyOrToast(R.string.update_failed, msgId, handler);
        thread.quitSafely();
    }
}
