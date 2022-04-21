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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MenuItem(
    val title: String,
    val contentDescription: String,
    val enabled: Boolean = true,
    val icon: Painter? = null,
    val iconImageVector: ImageVector? = null,
    val onClick: () -> Unit = {},
)

@Composable
fun AppBarMenu(
    menuIcon: @Composable () -> Unit,
    menuItems: List<MenuItem>,
    expanded: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(expanded) }
    Box(Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(
            onClick = {
                if (!menuExpanded) menuExpanded = true
            },
            content = menuIcon
        )
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = {
            menuExpanded = false
        },
        modifier = Modifier.fillMaxWidth(fraction = 0.35f)
    ) {
        menuItems.forEach {
            DropdownMenuItem(
                enabled = it.enabled,
                leadingIcon = {
                    when {
                        it.icon != null -> Icon(
                            painter = it.icon,
                            contentDescription = it.contentDescription
                        )
                        it.iconImageVector != null -> Icon(
                            imageVector = it.iconImageVector,
                            contentDescription = it.contentDescription
                        )
                        else -> Spacer(modifier = Modifier.size(24.dp))
                    }
                },
                text = {
                    Text(
                        text = it.title,
                        fontWeight = FontWeight.Bold
                    )
                },
                onClick = {
                    menuExpanded = false
                    it.onClick()
                },
            )
        }
    }
}