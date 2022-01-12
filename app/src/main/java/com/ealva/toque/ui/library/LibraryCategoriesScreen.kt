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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.parcelize.Parcelize

abstract class BaseLibraryItemsScreen : ComposeKey()

@Immutable
@Parcelize
data class LibraryCategoriesScreen(private val noArg: String = "") : BaseLibraryItemsScreen() {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(LibraryItemsViewModel(backstack)) }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<LibraryItemsViewModel>()
    val inPortrait = LocalScreenConfig.current.inPortrait

    LazyVerticalGrid(
      cells = GridCells.Fixed(if (inPortrait) 2 else 4),
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false),
      contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
      items(viewModel.getItems()) { item ->
        LibraryCategory(
          item = item,
          iconSize = 36.dp,
          textStyle = toqueTypography.subtitle1,
          textStartPadding = 4.dp,
          onClick = { viewModel.goToItem(item.key) }
        )
      }
    }
  }
}

private interface LibraryItemsViewModel {
  fun getItems(): List<LibraryCategories.CategoryItem>
  fun goToItem(key: ComposeKey)

  companion object {
    operator fun invoke(backstack: Backstack): LibraryItemsViewModel =
      LibraryItemsViewModelImpl(backstack)
  }
}

private class LibraryItemsViewModelImpl(private val backstack: Backstack) : LibraryItemsViewModel {
  val categories = LibraryCategories()
  override fun goToItem(key: ComposeKey) {
    backstack.goToScreen(key)
  }

  override fun getItems(): List<LibraryCategories.CategoryItem> = categories.getItems()
}
