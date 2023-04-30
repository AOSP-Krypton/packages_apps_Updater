/*
 * Copyright (C) 2021-2023 AOSP-Krypton Project
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

package com.kosp.updater.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalUnitApi::class)
@Composable
fun CardContent(
    title: String,
    subtitle: String? = null,
    body: @Composable BoxScope.() -> Unit = {},
    leadingAction: @Composable BoxScope.() -> Unit = {},
    trailingAction: @Composable BoxScope.() -> Unit = {},
) {
    val state = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = state,
        enter = slideInHorizontally { fullWidth ->
            fullWidth
        } + fadeIn(),
        exit = slideOutHorizontally { fullWidth ->
            -fullWidth
        } + fadeOut()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                fontSize = TextUnit(20f, TextUnitType.Sp),
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = subtitle,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                body()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    leadingAction()
                }
                Box(
                    Modifier.weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    trailingAction()
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewCardContent() {
    CardContent(
        title = "Card title",
        subtitle = "Card subtitle",
        body = {
            Text(modifier = Modifier.align(Alignment.Center), text = "Body")
        },
        leadingAction = {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                Text("Leading")
            }
        },
        trailingAction = {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                Text("Trailing")
            }
        }
    )
}