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

package com.krypton.updater.callbacks;

import android.os.Bundle;

public interface ActivityCallbacks {
    public void restoreActivityState(Bundle bundle);
    public void onFetchedBuildInfo(Bundle bundle);
    public void fetchBuildInfoFailed();
    public void noUpdates();
    public void noInternet();
    public void setInitialProgress(long downloaded, long total);
    public void updateDownloadedSize(long downloaded, long total);
    public void updateDownloadProgress(int progress);
    public void onFinishedDownload();
    public void md5sumCheckPassed(boolean passed);
    public void onStartingUpdate();
    public void onStatusUpdate(int status, int percent);
    public void onFinishedUpdate(int errorCode);
}
