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

package com.krypton.updater.services

import android.annotation.StringRes
import android.app.Service
import android.content.Intent
import android.os.IBinder

import com.krypton.updater.model.data.ResponseCode
import com.krypton.updater.model.repos.AppRepository
import com.krypton.updater.R
import com.krypton.updater.util.NotificationHelper
import com.krypton.updater.UpdaterApplication

import io.reactivex.rxjava3.disposables.Disposable

import javax.inject.Inject

class UpdateCheckerService: Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var repository: AppRepository
    private var disposable: Disposable? = null

    @Inject
    fun setDependencies(helper: NotificationHelper, repo: AppRepository) {
        notificationHelper = helper
        repository = repo
    }

    override fun onCreate() {
        (application as UpdaterApplication).getComponent().inject(this)
        disposable = repository.getOTAResponsePublisher()
            .map { response -> response.status }
            .filter { status -> status != ResponseCode.EMPTY_RESPONSE &&
                status != ResponseCode.FETCHING }
            .subscribe { status ->
                if (status == ResponseCode.NEW_DATA) {
                    notifyUser(R.string.notify_new_update, R.string.notify_new_update_desc)
                } else if (status == ResponseCode.FAILED) {
                    notifyUser(R.string.notify_refresh_failed, R.string.notify_refresh_failed_desc)
                }
                stopSelf()
            }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        repository.fetchBuildInfo()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        disposable?.let {
            if (!it.isDisposed()) it.dispose()
        }
    }

    private fun notifyUser(@StringRes titleId: Int, @StringRes descId: Int) {
        notificationHelper.showCancellableNotification(titleId, descId)
    }
}
