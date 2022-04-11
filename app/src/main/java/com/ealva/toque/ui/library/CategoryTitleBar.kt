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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.common.PopupMenu
import com.ealva.toque.ui.common.PopupMenuItem
import com.ealva.toque.ui.theme.toqueTypography

@Composable
fun CategoryTitleBar(
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  contentColor: Color,
  alphaPercentage: Float
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    CategoryTitle(categoryItem, contentColor, alphaPercentage)
    if (menuItems.isNotEmpty())
      Spacer(modifier = Modifier.weight(1.0F, fill = true))
    PopupMenu(items = menuItems)
  }
}

@Composable
private fun CategoryTitle(
  categoryItem: LibraryCategories.CategoryItem,
  contentColor: Color,
  alphaPercentage: Float
) {
  LibraryCategory(
    item = categoryItem,
    boxSize = 48.dp,
    iconSize = 38.dp,
    textStyle = toqueTypography.h5,
    padding = PaddingValues(vertical = 8.dp),
    textStartPadding = 10.dp,
    contentColor = contentColor,
    alphaPercentage = alphaPercentage
  )
}
