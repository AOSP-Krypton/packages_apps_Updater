/*
 * Copyright (C) 2022 AOSP-Krypton Project
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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.krypton.updater.R

@Composable
fun DownloadCard(state: DownloadCardState) {
    val subtitleText = state.subtitleText.collectAsState(null)
    CardContent(
        title = state.titleText,
        subtitle = subtitleText.value,
        body = {
            val showProgress = state.shouldShowProgress.collectAsState(initial = false)
            if (showProgress.value) {
                val progressDescription = state.progressDescriptionText.collectAsState(
                    initial = stringResource(
                        id = R.string.downloading
                    )
                )
                val progress = state.progress.collectAsState(initial = 0f)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(text = progressDescription.value)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = progress.value / 100f
                    )
                }
            }
        },
        leadingAction = {
            CustomButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                text = state.leadingActionButtonText,
                onClick = {
                    state.triggerLeadingAction()
                }
            )
        },
        trailingAction = {
            val text = state.trailingActionButtonText.collectAsState(null)
            CustomButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp),
                text = text.value ?: stringResource(id = R.string.download),
                onClick = {
                    state.triggerTrailingAction()
                }
            )
        }
    )
}