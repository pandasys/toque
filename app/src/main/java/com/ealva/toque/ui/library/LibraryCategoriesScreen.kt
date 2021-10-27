/*
 * Copyright 2021 eAlva.com
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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.ArtistType.AlbumArtist
import com.ealva.toque.ui.library.ArtistType.SongArtist
import com.ealva.toque.ui.library.LibraryItemsViewModel.LibraryItem
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger("LibraryItemsScreen")

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
        LibraryItem(item = item) { viewModel.goToItem(item.key) }
      }
    }
  }
}

@Composable
private fun LibraryItem(
  item: LibraryItem,
  goToItem: (ComposeKey) -> Unit
) {
  Row(
    modifier = Modifier
      .padding(vertical = 8.dp)
      .clickable(onClick = { goToItem(item.key) }),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = rememberImagePainter(data = item.icon),
      contentDescription = item.title,
      modifier = Modifier.size(38.dp)
    )
    Text(
      text = item.title,
      style = MaterialTheme.typography.h6,
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(start = 4.dp)
    )
  }
}

private interface LibraryItemsViewModel {

  data class LibraryItem(
    @DrawableRes val icon: Int,
    val title: String,
    val key: ComposeKey
  )

  fun getItems(): List<LibraryItem>
  fun goToItem(key: ComposeKey)

  companion object {
    operator fun invoke(backstack: Backstack): LibraryItemsViewModel =
      LibraryItemsViewModelImpl(backstack)
  }
}

private class LibraryItemsViewModelImpl(private val backstack: Backstack) : LibraryItemsViewModel {
  override fun goToItem(key: ComposeKey) {
    backstack.goTo(key)
  }

  override fun getItems(): List<LibraryItem> = listOf(
    LibraryItem(R.drawable.ic_treble_clef, "All Songs", LibrarySongsScreen()),
    LibraryItem(R.drawable.ic_album, "Albums", AllAlbumsScreen()),
    LibraryItem(R.drawable.ic_microphone, "Artists", AllArtistsScreen(SongArtist)),
    LibraryItem(R.drawable.ic_account_box, "Album Artists", AllArtistsScreen(AlbumArtist)),
    LibraryItem(R.drawable.ic_guitar_acoustic, "Genres", AllGenresScreen()),
    LibraryItem(R.drawable.ic_person, "Composers", AllComposersScreen()),
    LibraryItem(R.drawable.ic_list, "Playlists", LibrarySongsScreen()),
  )
}
