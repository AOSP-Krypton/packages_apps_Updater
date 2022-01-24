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

package com.krypton.updater.ui

import android.content.Intent
import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import com.krypton.updater.R
import com.krypton.updater.data.download.DownloadState
import com.krypton.updater.databinding.CardViewLayoutBinding
import com.krypton.updater.viewmodel.DownloadViewModel
import com.krypton.updater.viewmodel.MainViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CardFragment : Fragment(R.layout.card_view_layout) {
    private val mainViewModel: MainViewModel by activityViewModels()
    private val downloadViewModel: DownloadViewModel by activityViewModels()
    private lateinit var binding: CardViewLayoutBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = CardViewLayoutBinding.bind(view).also {
            it.lifecycleOwner = viewLifecycleOwner
            it.mainViewModel = mainViewModel
            it.downloadViewModel = downloadViewModel
            it.changelogButton.setOnClickListener {
                startActivity(Intent(context, ChangelogActivity::class.java))
            }
            it.actionButton.setOnClickListener { performAction() }
        }
    }

    override fun onStart() {
        super.onStart()
        downloadViewModel.downloadState.observe(this) {
            updateActionButtonText(it)
            binding.downloadGroup.visibility = if (it.idle) View.GONE else View.VISIBLE
            updateDownloadText(it)
            binding.downloadProgress.isIndeterminate = it.waiting
        }
        downloadViewModel.downloadProgress.observe(this) {
            updateDownloadProgress(it)
        }
    }

    private fun performAction() {
        downloadViewModel.downloadState.value?.let { state ->
            if (state.idle) {
                mainViewModel.updateInfo.value?.let {
                    downloadViewModel.startDownload(it.buildInfo)
                }
            } else if (state.downloading) {
                downloadViewModel.cancelDownload()
            }
            return@let
        }
    }

    private fun updateActionButtonText(state: DownloadState) {
        when {
            state.idle || state.failed -> binding.actionButton.setText(R.string.download)
            state.waiting || state.downloading -> binding.actionButton.setText(android.R.string.cancel)
            state.finished -> binding.actionButton.setText(R.string.update)
        }
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
}