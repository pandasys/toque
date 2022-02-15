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

package com.ealva.toque.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.PopupMenuItem
import com.google.accompanist.insets.statusBarsPadding

@Composable
fun CategoryScreenHeader(
  viewModel: ActionsViewModel,
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  backTo: String,
  back: () -> Unit,
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp)
  ) {
    val notInSelectionMode = !selectedItems.inSelectionMode
    if (notInSelectionMode) {
      Spacer(modifier = Modifier.height(2.dp))
      BackToButton(
        modifier = Modifier,
        backTo = backTo,
        contentColor = LocalContentColor.current,
        ovalColor = Color.Transparent,
        back = back,
      )
      Spacer(modifier = Modifier.height(22.dp))
    }
    CategoryTitleBar(categoryItem, menuItems)
    Spacer(modifier = Modifier.height(10.dp))
    LibraryItemsActions(
      itemCount = itemCount,
      selectedItems = selectedItems,
      viewModel = viewModel,
      selectActions = selectActions
    )
  }
}