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

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.databinding.DataBindingUtil

import com.krypton.updater.R
import com.krypton.updater.databinding.ActivityMainBinding
import com.krypton.updater.viewmodel.MainViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

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
    }

    override fun onStart() {
        super.onStart()
        binding.cardFragment.background.alpha = 120
        mainViewModel.updateFailedEvent.observe(this) {
            if (!it.hasBeenHandled) {
                Toast.makeText(this, it.getOrNull(), Toast.LENGTH_LONG).show()
            }
        }
        mainViewModel.lastCheckedTime.observe(this) { refreshUpdateStatusVisibilityAndText() }
        mainViewModel.isCheckingForUpdate.observe(this) { refreshUpdateStatusVisibilityAndText() }
        mainViewModel.updateInfo.observe(this) {
            showUpdateCardView()
        }
    }

    private fun refreshUpdateStatusVisibilityAndText() {
        when {
            mainViewModel.isCheckingForUpdate.value == true ->
                binding.updateStatus.apply {
                    text = getString(R.string.checking_for_update)
                    visibility = View.VISIBLE
                }
            mainViewModel.lastCheckedTime.value != null ->
                binding.updateStatus.apply {
                    text = getString(
                        R.string.last_checked_time_format,
                        mainViewModel.lastCheckedTime.value
                    )
                    visibility = View.VISIBLE
                }
            else -> binding.updateStatus.visibility = View.GONE
        }
    }

    private fun showUpdateCardView() {
        (binding.root as MotionLayout).transitionToEnd()
    }
}