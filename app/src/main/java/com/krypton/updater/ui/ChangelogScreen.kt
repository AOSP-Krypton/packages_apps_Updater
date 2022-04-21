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

package com.krypton.updater.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.navigation.NavHostController

import com.krypton.updater.R
import com.krypton.updater.viewmodel.ChangelogViewModel


import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    changelogViewModel: ChangelogViewModel,
    navHostController: NavHostController
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = stringResource(R.string.changelog)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navHostController.popBackStack() }) {
                        Icon(
                            modifier = Modifier.padding(start = 12.dp),
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.changelog_back_button_desc)
                        )
                    }
                },
            )
        }
    ) {
        val changelogListState = changelogViewModel.changelog.collectAsState(emptyList())
        val changelogList by remember { changelogListState }
        Box(modifier = Modifier.padding(start = 24.dp, end = 16.dp)) {
            if (changelogList.isEmpty()) {
                Text(text = stringResource(id = R.string.changelog_unavailable))
            } else {
                ChangelogList(changelogList)
            }
        }
    }
}

@Composable
fun ChangelogList(changelogList: List<Pair<Date, String?>>) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val dateFormatInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale)
    LazyColumn {
        items(changelogList) {
            SelectionContainer {
                Text(
                    text = buildAnnotatedString {
                        if (it.second != null) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(dateFormatInstance.format(it.first))
                            }
                            append("\n")
                            append(it.second!!)
                        }
                    }
                )
            }
        }
    }
}