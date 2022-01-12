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

package com.ealva.toque.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.toque.ui.theme.toqueTypography

@Composable
fun LibraryCategory(
  item: LibraryCategories.CategoryItem,
  boxSize: Dp = 42.dp,
  iconSize: Dp = 34.dp,
  textStyle: TextStyle = toqueTypography.subtitle1,
  padding: PaddingValues = PaddingValues(vertical = 8.dp),
  textStartPadding: Dp = 4.dp,
  onClick: () -> Unit = {}
) {
  Row(
    modifier = Modifier
      .clickable(onClick = onClick)
      .padding(paddingValues = padding),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(boxSize)
        .clip(RoundedCornerShape(50))
        .background(item.color),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painter = rememberImagePainter(data = item.icon),
        contentDescription = item.title,
        modifier = Modifier.size(iconSize),
        tint = Color.White
      )
    }
    Text(
      text = item.title,
      style = textStyle,
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(start = textStartPadding)
    )
  }
}
