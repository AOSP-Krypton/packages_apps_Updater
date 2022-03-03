/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import com.krypton.updater.databinding.FragmentUpdateBinding
import com.krypton.updater.R
import com.krypton.updater.data.update.UpdateState
import com.krypton.updater.services.UpdateInstallerService
import com.krypton.updater.viewmodel.UpdateViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UpdateFragment : Fragment(R.layout.fragment_update) {
    private val updateViewModel: UpdateViewModel by activityViewModels()

    private lateinit var binding: FragmentUpdateBinding

    private var bound = false
    private var updateInstallerService: UpdateInstallerService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logD("onServiceConnected")
            updateInstallerService = (service as? UpdateInstallerService.ServiceBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logD("onServiceDisconnected")
            updateInstallerService = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentUpdateBinding.bind(view).also {
            it.lifecycleOwner = viewLifecycleOwner
            it.updateViewModel = updateViewModel
        }
    }

    override fun onStart() {
        super.onStart()
        logD("binding service")
        bound = context?.bindService(
            Intent(context, UpdateInstallerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        ) == true
        logD("bound = $bound")
        updateViewModel.updateState.observe(this) {
            logD("updateState = $it")
            updateLeftActionButton(it)
            updateRightActionButton()
            binding.updateProgressGroup.visibility =
                if (it.idle || it.finished) View.GONE else View.VISIBLE
            updateInstallationText(it)
        }
        updateViewModel.updateFailed.observe(this) { message ->
            message.getOrNull()?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
        updateViewModel.updateProgress.observe(this) {
            updateInstallationProgress(it)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            context?.unbindService(serviceConnection)
            bound = false
        }
    }

    private fun updateLeftActionButton(updateState: UpdateState) {
        when {
            updateState.updating -> binding.leftActionButton.apply {
                setText(R.string.pause)
                setOnClickListener {
                    logD("pausing update")
                    updateInstallerService?.pauseOrResumeUpdate()
                }
                visibility = View.VISIBLE
            }
            updateState.paused -> binding.leftActionButton.apply {
                setText(R.string.resume)
                setOnClickListener {
                    logD("resuming update")
                    updateInstallerService?.pauseOrResumeUpdate()
                }
                visibility = View.VISIBLE
            }
            else -> binding.leftActionButton.apply {
                text = ""
                setOnClickListener(null)
                visibility = View.INVISIBLE
            }
        }
    }

    private fun updateRightActionButton() {
        updateViewModel.updateState.value?.let {
            when {
                it.updating || it.paused -> binding.rightActionButton.apply {
                    setText(android.R.string.cancel)
                    setOnClickListener {
                        logD("cancelling update")
                        updateInstallerService?.cancelUpdate()
                    }
                }
                it.idle || it.initializing || it.failed ->
                    binding.rightActionButton.apply {
                        setText(R.string.update)
                        setOnClickListener { startUpdate() }
                    }
                it.finished -> {
                    binding.rightActionButton.apply {
                        setText(R.string.reboot)
                        setOnClickListener {
                            updateInstallerService?.reboot()
                        }
                    }
                }
            }
            return@let
        }
    }

    private fun startUpdate() {
        logD("starting update")
        context?.startService(Intent(context, UpdateInstallerService::class.java).apply {
            action = UpdateInstallerService.ACTION_START_UPDATE
        })
    }

    private fun updateInstallationText(state: UpdateState) {
        when {
            state.initializing -> binding.updateProgressText.setText(R.string.initializing)
            state.updating -> binding.updateProgressText.setText(R.string.installing_update)
            state.paused -> binding.updateProgressText.setText(R.string.installation_paused)
            state.failed -> binding.updateProgressText.setText(R.string.installation_failed)
        }
    }

    private fun updateInstallationProgress(progress: Float) {
        updateViewModel.updateState.value?.let {
            if (it.updating) {
                binding.updateProgress.progress = progress.toInt()
                binding.updateProgressText.text =
                    getString(R.string.installing_update_format, String.format("%.2f", progress))
            }
        }
    }

    companion object {
        private const val TAG = "UpdateFragment"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}