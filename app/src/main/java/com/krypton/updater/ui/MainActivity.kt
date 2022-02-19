/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.krypton.updater.ui

import android.annotation.StringRes
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow

import com.krypton.updater.R
import com.krypton.updater.databinding.ActivityMainBinding
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel
import com.krypton.updater.viewmodel.UpdateViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    private val localUpgradeFileContract =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it != null) {
                updateViewModel.setupLocalUpgrade(it)
            }
        }

    private val documentTreeContract =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it == null) {
                finish()
                return@registerForActivityResult
            }
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, flags)
        }

    private var transitionPending = false

    private var stateRestoreProgressDialog: AlertDialog? = null
    private var fileExportProgressDialog: AlertDialog? = null
    private var fileCopyProgressDialog: AlertDialog? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(
            this, R.layout.activity_main,
        ).also {
            it.mainViewModel = mainViewModel
            it.lifecycleOwner = this
        }
        checkExportFolderPermission()
        supportFragmentManager.addFragmentOnAttachListener { _, _ ->
            logD("onFragmentAttached")
            if (transitionPending) {
                logD("completing pending transition")
                (binding.root as MotionLayout).transitionToEnd()
                transitionPending = false
            }
        }
    }

    private fun checkExportFolderPermission() {
        val hasPerms = contentResolver.persistedUriPermissions.firstOrNull()?.let {
            it.isReadPermission && it.isWritePermission
        } ?: false
        if (!hasPerms) {
            documentTreeContract.launch(null)
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.updateFailedEvent.observe(this) {
            if (!it.hasBeenHandled) {
                Toast.makeText(this, it.getOrNull(), Toast.LENGTH_LONG).show()
            }
        }
        mainViewModel.lastCheckedTime.observe(this) { refreshUpdateView() }
        mainViewModel.isCheckingForUpdate.observe(this) { refreshUpdateView() }
        mainViewModel.updateInfoUnavailable.observe(this) { updateCardFragment() }
        mainViewModel.newUpdateAvailable.observe(this) { updateCardFragment() }
        mainViewModel.noUpdateAvailable.observe(this) { updateCardFragment() }
        downloadViewModel.stateRestoreFinished.observe(this) {
            stateRestoreProgressDialog?.dismiss()
            stateRestoreProgressDialog = if (!it) {
                createProgressDialog(R.string.restoring_state).apply { show() }
            } else {
                null
            }
        }
        downloadViewModel.exportingFile.observe(this) {
            fileExportProgressDialog?.dismiss()
            fileExportProgressDialog = if (it) {
                transitionToStart()
                createProgressDialog(R.string.exporting_file).apply { show() }
            } else {
                null
            }
        }
        downloadViewModel.exportingFailed.observe(this) {
            it.getOrNull()?.let { error ->
                Toast.makeText(this, getString(R.string.exporting_failed, error), Toast.LENGTH_LONG)
                    .show()
            }
        }
        updateViewModel.readyForUpdate.observe(this) { updateCardFragment() }
        updateViewModel.isUpdating.observe(this) { updateCardFragment() }
        updateViewModel.copyingFile.observe(this) {
            fileCopyProgressDialog?.dismiss()
            fileCopyProgressDialog = if (it) {
                transitionToStart()
                createProgressDialog(R.string.copying_file).apply { show() }
            } else {
                null
            }
        }
        updateViewModel.copyFailed.observe(this) {
            it.getOrNull()?.let { error ->
                Toast.makeText(this, getString(R.string.copying_failed, error), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun createProgressDialog(@StringRes titleId: Int): AlertDialog =
        AlertDialog.Builder(this)
            .setTitle(titleId)
            .setCancelable(false)
            .create().also {
                it.setView(
                    LayoutInflater.from(this).inflate(
                        R.layout.copy_progress_bar,
                        it.findViewById<FrameLayout>(R.id.customPanel),
                        true
                    )
                )
                it.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.alert_dialog_background,
                        resources.newTheme().apply {
                            applyStyle(R.style.Theme_Updater, true)
                        })
                )
            }

    private fun updateCardFragment() {
        when {
            updateViewModel.isUpdating.value == true ||
                    updateViewModel.readyForUpdate.value == true -> showUpdateFragment()
            mainViewModel.newUpdateAvailable.value == true -> showDownloadFragment()
            mainViewModel.noUpdateAvailable.value == true -> showNoUpdateFragment()
            else -> transitionToStart()
        }
    }

    fun showPopupMenu(view: View) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.updater_menu, menu)
            setOnMenuItemClickListener {
                if (it.itemId == R.id.local_upgrade) {
                    if (!downloadViewModel.isDownloading) {
                        localUpgradeFileContract.launch(ZIP_MIME)
                    }
                    true
                } else {
                    false
                }
            }
            show()
        }
    }

    private fun refreshUpdateView() {
        when {
            mainViewModel.isCheckingForUpdate.value == true ->
                binding.updateStatus.apply {
                    text = getString(R.string.checking_for_update)
                    visibility = View.VISIBLE
                }
            mainViewModel.lastCheckedTime.value != null -> {
                binding.updateStatus.post {
                    binding.updateStatus.apply {
                        text = getString(
                            R.string.last_checked_time_format,
                            mainViewModel.lastCheckedTime.value
                        )
                        visibility = View.VISIBLE
                    }
                }
            }
            else -> binding.updateStatus.visibility = View.GONE
        }
    }

    private fun showNoUpdateFragment() {
        logD("showNoUpdateFragment")
        showFragment(NoUpdateFragment(), NO_UPDATE_FRAGMENT_TAG)
    }

    private fun showDownloadFragment() {
        logD("showDownloadFragment")
        showFragment(DownloadFragment(), DOWNLOAD_FRAGMENT_TAG)
    }

    private fun showUpdateFragment() {
        logD("showUpdateFragment")
        showFragment(UpdateFragment(), UPDATE_FRAGMENT_TAG)
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val root = (binding.root as MotionLayout)
        val fragmentAttached = supportFragmentManager.findFragmentByTag(tag) != null
        if (fragmentAttached) {
            if (root.currentState != root.endState) {
                root.transitionToEnd()
            }
            return
        }
        if (root.currentState == root.endState) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
                replace(R.id.card_fragment_container, fragment, tag)
            }
        } else {
            transitionPending = true
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                setCustomAnimations(0, R.anim.slide_out)
                replace(R.id.card_fragment_container, fragment, tag)
            }
        }
    }

    private fun transitionToStart() {
        transitionPending = false
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.commitNow { remove(it) }
        }
        (binding.root as MotionLayout).transitionToStart()
    }

    override fun onStop() {
        fileCopyProgressDialog?.dismiss()
        fileExportProgressDialog?.dismiss()
        stateRestoreProgressDialog?.dismiss()
        super.onStop()
    }

    companion object {
        private val ZIP_MIME = arrayOf("application/zip")

        private const val NO_UPDATE_FRAGMENT_TAG = "no_update_fragment"
        private const val DOWNLOAD_FRAGMENT_TAG = "download_fragment"
        private const val UPDATE_FRAGMENT_TAG = "update_fragment"

        private const val TAG = "MainActivity"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}