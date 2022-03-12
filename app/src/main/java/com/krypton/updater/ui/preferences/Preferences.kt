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

package com.krypton.updater.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp

import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun Preference(
    title: String,
    summary: String,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    startWidget: @Composable (() -> Unit)? = null,
    endWidget: @Composable (() -> Unit)? = null,
    bottomWidget: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = { onClick() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startWidget != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterVertically)
            ) {
                startWidget()
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f),
                maxLines = 4
            )
            if (bottomWidget != null) {
                Box(modifier = Modifier.padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    bottomWidget()
                }
            }
        }
        if (endWidget != null) {
            Box(
                modifier = Modifier.padding(start = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                endWidget()
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Preference(
        title = title,
        summary = summary,
        clickable = clickable,
        onClick = onClick,
        endWidget = {
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.inverseSurface
                )
            )
        },
    )
}

@OptIn(ExperimentalUnitApi::class)
@Composable
fun DiscreteSeekBarPreference(
    title: String,
    summary: String,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    min: Int,
    max: Int,
    value: Int,
    showProgressText: Boolean = false,
    onProgressChanged: (Int) -> Unit = {},
    onChangeFinished: (Int) -> Unit = {}
) {
    var position by remember { mutableStateOf(value) }
    Preference(
        title = title,
        summary = summary,
        clickable = clickable,
        onClick = onClick,
        bottomWidget = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    modifier = Modifier.weight(1f),
                    onValueChange = {
                        position = if (it > position) {
                            floor(it).toInt()
                        } else {
                            ceil(it).toInt()
                        }
                        onProgressChanged(position)
                    },
                    onValueChangeFinished = {
                        onChangeFinished(position)
                    },
                    valueRange = min.toFloat()..max.toFloat(),
                    value = position.toFloat(),
                )
                if (showProgressText) {
                    Text(
                        modifier = Modifier.width(36.dp),
                        text = position.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
            }
        },
    )
}