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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.PopupMenuItem
import com.ealva.toque.ui.common.timesAlpha
import com.google.accompanist.insets.statusBarsPadding

private const val ID_PIN_TO_TOP = "PIN_TO_TOP_ID"

@Composable
fun CategoryScreenHeader(
  viewModel: ActionsViewModel,
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  backTo: String,
  back: () -> Unit,
  scrollConnection: HeightResizeScrollConnection = HeightResizeScrollConnection(),
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  val heightSubtrahend = scrollConnection.heightSubtrahend.collectAsState()
  CategoryScreenHeader(
    viewModel = viewModel,
    categoryItem = categoryItem,
    menuItems = menuItems,
    itemCount = itemCount,
    selectedItems = selectedItems,
    backTo = backTo,
    back = back,
    heightSubtrahend = heightSubtrahend.value,
    scrollConnection = scrollConnection,
    selectActions = selectActions
  )
}

@Composable
private fun CategoryScreenHeader(
  viewModel: ActionsViewModel,
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  backTo: String,
  back: () -> Unit,
  heightSubtrahend: Int,
  scrollConnection: HeightResizeScrollConnection,
  selectActions: @Composable ((Dp) -> Unit)? = null,
) {
  val maxSubtrahend = scrollConnection.maxSubtrahend
  val alphaPercentage =
    if (maxSubtrahend < Int.MAX_VALUE) 1F - (heightSubtrahend.toFloat()) / maxSubtrahend else 1F

  Layout(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp),
    content = {
      val contentColor = LocalContentColor.current.timesAlpha(alphaPercentage)
      val notInSelectionMode = !selectedItems.inSelectionMode
      if (notInSelectionMode) {
        Column(modifier = Modifier.layoutId(ID_PIN_TO_TOP)) {
          Spacer(modifier = Modifier.height(2.dp))
          BackToButton(
            modifier = Modifier,
            backTo = backTo,
            color = contentColor,
            ovalColor = Color.Transparent,
            back = back,
          )
          Spacer(modifier = Modifier.height(22.dp))
        }
      }
      CategoryTitleBar(categoryItem, menuItems, contentColor, alphaPercentage)
      Spacer(modifier = Modifier.height(10.dp))
      LibraryItemsActions(
        itemCount = itemCount,
        selectedItems = selectedItems,
        viewModel = viewModel,
        selectActions = selectActions
      )
    },
    measurePolicy = BottomUpResizeHeightMeasurePolicy(
      heightSubtrahend = heightSubtrahend,
      scrollConnection = scrollConnection,
      pinToTopId = ID_PIN_TO_TOP
    )
  )
}
