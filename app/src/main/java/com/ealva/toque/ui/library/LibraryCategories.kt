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

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.navigation.NullComposeKey
import com.ealva.toque.ui.library.LibraryCategories.CategoryItem
import com.ealva.toque.ui.theme.AlbumArtists
import com.ealva.toque.ui.theme.Albums
import com.ealva.toque.ui.theme.AllSongs
import com.ealva.toque.ui.theme.Artists
import com.ealva.toque.ui.theme.Composers
import com.ealva.toque.ui.theme.Genres
import com.ealva.toque.ui.theme.Playlists

/**
 * Although this info is currently static, we'll put an interface around it in case we later need
 * to build some things dynamically.
 */
interface LibraryCategories {
  @Immutable
  data class CategoryItem(
    @DrawableRes val icon: Int,
    val title: String,
    val key: ComposeKey,
    val color: Color
  ) {
    companion object {
      val NullCategoryItem = CategoryItem(0, "Null", NullComposeKey, Color.Unspecified)
    }
  }

  fun getItems(): List<CategoryItem>

  operator fun get(key: ComposeKey): CategoryItem

  companion object {
    operator fun invoke(): LibraryCategories = LibraryCategoriesImpl()
  }
}

private class LibraryCategoriesImpl : LibraryCategories {
  private val categoryList = listOf(
    CategoryItem(
      R.drawable.ic_treble_clef,
      "All Songs",
      LibrarySongsScreen(),
      Color.AllSongs
    ),
    CategoryItem(
      R.drawable.ic_album,
      "Albums",
      AlbumsScreen(),
      Color.Albums
    ),
    CategoryItem(
      R.drawable.ic_microphone,
      "Artists",
      ArtistsScreen(ArtistType.SongArtist),
      Color.Artists
    ),
    CategoryItem(
      R.drawable.ic_account_box,
      "Album Artists",
      ArtistsScreen(ArtistType.AlbumArtist),
      Color.AlbumArtists
    ),
    CategoryItem(
      R.drawable.ic_guitar_acoustic,
      "Genres",
      GenresScreen(),
      Color.Genres
    ),
    CategoryItem(
      R.drawable.ic_person,
      "Composers",
      ComposersScreen(),
      Color.Composers
    ),
    CategoryItem(
      R.drawable.ic_list,
      "Playlists",
      PlaylistsScreen(),
      Color.Playlists
    ),
  )

  override fun getItems(): List<CategoryItem> = categoryList

  override fun get(key: ComposeKey): CategoryItem =
    categoryList.find { it.key == key } ?: CategoryItem.NullCategoryItem

}
