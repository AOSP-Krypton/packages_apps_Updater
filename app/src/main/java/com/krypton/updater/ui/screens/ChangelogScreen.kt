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

package com.krypton.updater.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.google.accompanist.systemuicontroller.SystemUiController
import com.krypton.updater.R
import com.krypton.updater.viewmodel.ChangelogViewModel

import java.text.DateFormat

@Composable
fun ChangelogScreen(
    changelogViewModel: ChangelogViewModel = hiltViewModel(),
    systemUiController: SystemUiController,
    navHostController: NavHostController
) {
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val locale = LocalContext.current.resources.configuration.locales[0]
    val changelogListState = changelogViewModel.changelog.collectAsState(emptyList())
    val changelogList by remember { changelogListState }
    CollapsingToolbarScreen(
        title = stringResource(R.string.changelog),
        backButtonContentDescription = stringResource(R.string.changelog_back_button_desc),
        onBackButtonPressed = { navHostController.popBackStack() },
        onStatusBarColorUpdateRequest = {
            systemUiController.setStatusBarColor(
                color = it,
                darkIcons = !isSystemInDarkTheme
            )
        },
    ) {
        val dateFormatInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale)
        if (changelogList.isEmpty()) {
            item {
                Text(text = stringResource(id = R.string.changelog_unavailable))
            }
        } else {
            items(changelogList) {
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp),
                        text = buildAnnotatedString {
                            it.second?.let { changelog ->
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(dateFormatInstance.format(it.first))
                                }
                                append("\n")
                                append(changelog)
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}