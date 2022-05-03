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

package com.krypton.updater.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

import com.krypton.updater.ui.preferences.Preference

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingToolbarScreen(
    title: String,
    backButtonContentDescription: String,
    onBackButtonPressed: () -> Unit,
    onStatusBarColorUpdateRequest: (Color) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    // height of appbar
    val toolbarHeight = 48.dp
    val toolbarHeightPx = with(LocalDensity.current) { toolbarHeight.roundToPx().toFloat() }
    // padding of big title from top
    val bigTitlePadding = 56.dp
    val bigTitlePaddingPx = with(LocalDensity.current) { bigTitlePadding.roundToPx().toFloat() }
    // offset of big title, updated with scroll position of column
    var offset by remember { mutableStateOf(bigTitlePaddingPx) }
    // alpha for big title offset
    val alphaForOffset = offset.coerceIn(
        -toolbarHeightPx,
        0f
    ).absoluteValue / toolbarHeightPx
    val bigTitleAlpha = offset.coerceIn(0f, toolbarHeightPx / 2) / (toolbarHeightPx / 2)
    // container color of toolbar
    val toolbarColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        alphaForOffset
    )
    LaunchedEffect(toolbarColor) {
        onStatusBarColorUpdateRequest(toolbarColor)
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                offset += consumed.y
                return super.onPostScroll(consumed, available, source)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(toolbarHeight)
                .background(toolbarColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackButtonPressed) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = backButtonContentDescription,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
                    .alpha(alphaForOffset),
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Surface(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .height(bigTitlePadding)
                    .padding(horizontal = 24.dp)
                    .alpha(bigTitleAlpha)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = offset.roundToInt()
                        )
                    },
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyColumn(
                modifier = Modifier.nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = bigTitlePadding + 64.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
                content = content
            )
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewCollapsingToolbarScreen() {
    CollapsingToolbarScreen(
        title = "Collapsing toolbar",
        backButtonContentDescription = "Back button",
        onBackButtonPressed = {},
        onStatusBarColorUpdateRequest = {}
    ) {
        items(50) { index ->
            Preference(
                "Preference $index",
                summary = if (index % 2 == 0) "Preference summary" else null
            )
        }
    }
}