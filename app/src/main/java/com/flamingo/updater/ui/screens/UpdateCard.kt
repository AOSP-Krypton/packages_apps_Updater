/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.flamingo.updater.R
import com.flamingo.updater.ui.states.UpdateCardState
import com.flamingo.updater.ui.widgets.CustomButton

@Composable
fun UpdateCard(state: UpdateCardState) {
    CardContent(
        title = state.titleText,
        body = {
            val showProgress by state.shouldShowProgress.collectAsState(initial = false)
            if (showProgress) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val progressText by state.progressDescriptionText.collectAsState(null)
                    progressText?.let {
                        Text(text = it)
                    }
                    val progress by state.progress.collectAsState(initial = 0f)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = progress / 100f
                    )
                }
            }
        },
        leadingAction = {
            val showLeading by state.shouldShowLeadingActionButton.collectAsState(false)
            if (showLeading) {
                val text by state.leadingActionButtonText.collectAsState(null)
                text?.let {
                    CustomButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        text = it,
                        onClick = {
                            state.leadingAction()
                        }
                    )
                }
            }
        },
        trailingAction = {
            val text by state.trailingActionButtonText.collectAsState(stringResource(R.string.install_update))
            CustomButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                text = text,
                onClick = {
                    state.trailingAction()
                }
            )
        }
    )
}