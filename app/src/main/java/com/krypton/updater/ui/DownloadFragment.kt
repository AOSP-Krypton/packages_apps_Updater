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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import com.krypton.updater.data.download.DownloadState
import com.krypton.updater.databinding.FragmentDownloadBinding
import com.krypton.updater.R
import com.krypton.updater.services.UpdateInstallerService
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadFragment : Fragment(R.layout.fragment_download) {
    private val mainViewModel: MainViewModel by activityViewModels()
    private val downloadViewModel: DownloadViewModel by activityViewModels()

    private lateinit var binding: FragmentDownloadBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDownloadBinding.bind(view).also {
            it.lifecycleOwner = viewLifecycleOwner
            it.mainViewModel = mainViewModel
            it.downloadViewModel = downloadViewModel
            it.changelogButton.setOnClickListener {
                startActivity(Intent(context, ChangelogActivity::class.java))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        downloadViewModel.downloadState.observe(this) {
            logD("downloadState = $it")
            updateRightActionButton()
            binding.downloadProgressGroup.visibility = if (it.idle) View.GONE else View.VISIBLE
            updateDownloadText(it)
        }
        downloadViewModel.downloadFailed.observe(this) { message ->
            message.getOrNull()?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
        downloadViewModel.downloadProgress.observe(this) {
            updateDownloadProgress(it)
        }
    }

    private fun updateRightActionButton() {
        downloadViewModel.downloadState.value?.let { downloadState ->
            when {
                downloadState.idle || downloadState.failed -> binding.actionButton.apply {
                    setText(R.string.download)
                    setOnClickListener { startDownload() }
                }
                downloadState.waiting || downloadState.downloading -> binding.actionButton.apply {
                    setText(android.R.string.cancel)
                    setOnClickListener {
                        logD("cancelling download")
                        downloadViewModel.cancelDownload()
                    }
                }
                downloadState.finished ->
                    binding.actionButton.apply {
                        setText(R.string.update)
                        setOnClickListener { startUpdate() }
                    }
            }
            return@let
        }
    }

    private fun startDownload() {
        logD("starting download")
        mainViewModel.updateInfo.value?.let { updateInfo ->
            updateInfo.buildInfo?.let { downloadViewModel.startDownload(it) }
        }
    }

    private fun startUpdate() {
        logD("starting update")
        context?.startService(Intent(context, UpdateInstallerService::class.java).apply {
            action = UpdateInstallerService.ACTION_START_UPDATE
        })
    }

    private fun updateDownloadText(state: DownloadState) {
        when {
            state.waiting -> binding.downloadProgressText.setText(R.string.waiting)
            state.downloading -> binding.downloadProgressText.setText(R.string.downloading)
            state.finished -> binding.downloadProgressText.setText(R.string.downloading_finished)
            state.failed -> binding.downloadProgressText.setText(R.string.downloading_failed)
        }
    }

    private fun updateDownloadProgress(progress: Int) {
        downloadViewModel.downloadState.value?.let {
            if (it.downloading || it.finished) {
                binding.downloadProgress.progress = progress
                if (it.downloading) {
                    binding.downloadProgressText.text =
                        getString(R.string.download_text_format, progress)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DownloadFragment"
        private val DEBUG: Boolean
            get() = Log.isLoggable(TAG, Log.DEBUG)

        private fun logD(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}