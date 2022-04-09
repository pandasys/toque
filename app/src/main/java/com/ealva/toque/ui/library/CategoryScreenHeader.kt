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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.PopupMenuItem
import com.google.accompanist.insets.statusBarsPadding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun CategoryScreenHeader(
  viewModel: ActionsViewModel,
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  backTo: String,
  back: () -> Unit,
  scrollConnection: CategoryHeaderScrollConnection = CategoryHeaderScrollConnection(),
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  val heightSubtrahend = scrollConnection.heightSubtrahend.collectAsState()
  CategoryScreenHeader(
    viewModel = viewModel,
    heightSubtrahend = heightSubtrahend.value,
    categoryItem = categoryItem,
    menuItems = menuItems,
    itemCount = itemCount,
    selectedItems = selectedItems,
    backTo = backTo,
    back = back,
    scrollConnection = scrollConnection,
    selectActions = selectActions
  )
}

@Composable
private fun CategoryScreenHeader(
  viewModel: ActionsViewModel,
  heightSubtrahend: Int,
  categoryItem: LibraryCategories.CategoryItem,
  menuItems: List<PopupMenuItem> = emptyList(),
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  backTo: String,
  back: () -> Unit,
  scrollConnection: CategoryHeaderScrollConnection,
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  Layout(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp),
    content = {
      val notInSelectionMode = !selectedItems.inSelectionMode
      if (notInSelectionMode) {
        Column {
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
  ) { measurables, constraints ->
   val placeables = measurables.map { measurable ->
      measurable.measure(constraints)
    }

    val width = placeables.maxOf { placeable -> placeable.width }
    val maxHeight = placeables.sumOf { placeable -> placeable.height }
    val minHeight = placeables.last().height
    if (heightSubtrahend == 0) scrollConnection.maxSubtrahend = maxHeight - minHeight
    val height = (maxHeight - heightSubtrahend)
      .coerceAtLeast(minHeight)

    layout(width, height) {
      var y = height
      placeables.asReversed().forEach { placeable ->
        y -= placeable.height
        placeable.place(x = 0, y = y)
      }
    }
  }
}

interface CategoryHeaderScrollConnection : NestedScrollConnection {
  val heightSubtrahend: StateFlow<Int>
  var maxSubtrahend: Int

  companion object {
    operator fun invoke(): CategoryHeaderScrollConnection = CategoryHeaderScrollConnectionImpl()
  }
}

private class CategoryHeaderScrollConnectionImpl : CategoryHeaderScrollConnection {
  override val heightSubtrahend = MutableStateFlow(0)
  override var maxSubtrahend = Int.MAX_VALUE

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    heightSubtrahend.value = (heightSubtrahend.value - available.y.roundToInt())
      .coerceIn(0..maxSubtrahend)
    return Offset.Zero
  }
}
