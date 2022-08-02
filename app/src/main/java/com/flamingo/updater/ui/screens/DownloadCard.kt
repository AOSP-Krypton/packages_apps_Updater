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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

import com.flamingo.updater.R
import com.flamingo.updater.ui.states.DownloadCardState
import com.flamingo.updater.ui.widgets.CustomButton

@Composable
fun DownloadCard(state: DownloadCardState) {
    val shouldShowDownloadSourceDialog by state.shouldShowDownloadSourceDialog.collectAsState(false)
    if (shouldShowDownloadSourceDialog) {
        val sources by state.downloadSources.collectAsState(initial = emptySet())
        if (sources.isNotEmpty()) {
            DownloadSourceDialog(
                dismissRequest = {
                    state.dismissDownloadSourceDialog()
                },
                confirmRequest = {
                    state.startDownloadWithSource(it)
                },
                sources = sources
            )
        }
    }
    val subtitleText by state.subtitleText.collectAsState(null)
    CardContent(
        title = state.titleText,
        subtitle = subtitleText,
        body = {
            val showProgress by state.shouldShowProgress.collectAsState(initial = false)
            if (showProgress) {
                val progressDescription by state.progressDescriptionText.collectAsState(initial = null)
                val progress by state.progress.collectAsState(initial = 0f)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    progressDescription?.let {
                        Text(text = it)
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = progress / 100f
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
            val text by state.trailingActionButtonText.collectAsState(null)
            CustomButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp),
                text = text ?: stringResource(id = R.string.download),
                onClick = {
                    state.triggerTrailingAction()
                }
            )
        }
    )
}

@Composable
fun DownloadSourceDialog(
    dismissRequest: () -> Unit,
    confirmRequest: (String) -> Unit,
    sources: Set<String>
) {
    var selectedSource by remember {
        mutableStateOf(sources.first())
    }
    AlertDialog(
        onDismissRequest = dismissRequest,
        confirmButton = {
            TextButton(onClick = { confirmRequest(selectedSource) }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        properties = DialogProperties(),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(text = stringResource(R.string.select_download_source))
        },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .fillMaxWidth()
            ) {
                sources.forEach {
                    RadioListItem(
                        selected = it == selectedSource,
                        onClick = {
                            selectedSource = it
                        },
                        title = it
                    )
                }
            }
        }
    )
}

@Composable
fun RadioListItem(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Preview
@Composable
fun PreviewRadioListItem() {
    RadioListItem(selected = true, onClick = { }, title = "Radio list item")
}