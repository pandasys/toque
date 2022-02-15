/*
 * Copyright 2022 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.toque.ui.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.ealva.toque.ui.theme.toqueTypography

@Composable
fun ListItemText(text: String, color: Color = Color.Unspecified) {
  Text(
    text = text,
    color = color,
    style = toqueTypography.listItemText,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
  )
}
