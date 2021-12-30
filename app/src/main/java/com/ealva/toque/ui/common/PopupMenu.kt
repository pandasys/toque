/*
 * Copyright 2021 Eric A. Snell
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.toque.R

@Composable
fun PopupMenu(
  modifier: Modifier = Modifier,
  items: List<PopupMenuItem>
) {
  if (items.isNotEmpty()) {
    var expanded by remember { mutableStateOf(false) }
    Box(
      modifier = Modifier
        .size(42.dp)
        .padding(8.dp)
        .clickable { expanded = true }
        .then(modifier),

      ) {
      Icon(
        painter = rememberImagePainter(data = R.drawable.ic_more_vert),
        contentDescription = stringResource(id = R.string.EmbeddedArtwork),
        modifier = Modifier.size(40.dp),
        tint = LocalContentColor.current
      )
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
      ) {
        items.forEach { item ->
          DropdownMenuItem(
            onClick = {
              expanded = false
              item.onClick()
            }
          ) {
            Text(text = item.title)
          }
        }
      }
    }
  }
}

data class PopupMenuItem(
  val title: String,
  val onClick: () -> Unit
)
