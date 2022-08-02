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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

import com.flamingo.support.compose.ui.layout.CollapsingToolbarLayout
import com.flamingo.updater.R
import com.flamingo.updater.ui.states.ChangelogScreenState
import com.flamingo.updater.ui.states.rememberChangelogScreenState

import java.text.DateFormat

@Composable
fun ChangelogScreen(
    navHostController: NavHostController,
    modifier: Modifier = Modifier,
    state: ChangelogScreenState = rememberChangelogScreenState()
) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val changelogs by state.changelog.collectAsState(emptyList())
    CollapsingToolbarLayout(
        modifier = modifier,
        title = stringResource(R.string.changelog),
        onBackButtonPressed = { navHostController.popBackStack() }
    ) {
        val dateFormatInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale)
        if (changelogs.isEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.changelog_unavailable), modifier = Modifier
                        .padding(start = 24.dp)
                        .fillMaxWidth()
                )
            }
        } else {
            items(changelogs) {
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .fillMaxWidth(),
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