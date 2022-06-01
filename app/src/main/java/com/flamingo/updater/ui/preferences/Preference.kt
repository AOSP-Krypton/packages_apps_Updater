/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    title: String,
    summary: String? = null,
    clickable: Boolean = true,
    onClick: () -> Unit = {},
    startWidget: @Composable (BoxScope.() -> Unit)? = null,
    endWidget: @Composable (BoxScope.() -> Unit)? = null,
    bottomWidget: @Composable (BoxScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startWidget != null) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
                content = startWidget
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            if (summary != null) {
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f),
                    maxLines = 4,
                )
            }
            if (bottomWidget != null) {
                Box(
                    modifier = Modifier.padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                    content = bottomWidget
                )
            }
        }
        if (endWidget != null) {
            Box(
                modifier = Modifier.padding(start = 6.dp),
                contentAlignment = Alignment.Center,
                content = endWidget
            )
        }
    }
}