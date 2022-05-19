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
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.navigation.NullComposeKey
import com.ealva.toque.ui.library.LibraryCategories.LibraryCategory
import com.ealva.toque.ui.library.LibraryCategories.Companion.AlbumArtists
import com.ealva.toque.ui.library.LibraryCategories.Companion.Albums
import com.ealva.toque.ui.library.LibraryCategories.Companion.AllSongs
import com.ealva.toque.ui.library.LibraryCategories.Companion.Artists
import com.ealva.toque.ui.library.LibraryCategories.Companion.Composers
import com.ealva.toque.ui.library.LibraryCategories.Companion.Genres
import com.ealva.toque.ui.library.LibraryCategories.Companion.Playlists
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
  data class LibraryCategory(
    @DrawableRes override val icon: Int,
    @StringRes override val title: Int,
    val key: ComposeKey,
    val color: Color
  ) : Category {
    companion object {
      val NullCategoryItem = LibraryCategory(0, R.string.None, NullComposeKey, Color.Unspecified)
    }
  }

  fun getItems(): List<LibraryCategory>

  operator fun get(key: ComposeKey): LibraryCategory

  companion object {
    operator fun invoke(): LibraryCategories = LibraryCategoriesImpl()

    val AllSongs = LibraryCategory(
      R.drawable.ic_treble_clef,
      R.string.AllSongs,
      LibrarySongsScreen(),
      Color.AllSongs
    )

    val Albums = LibraryCategory(
      R.drawable.ic_album,
      R.string.Albums,
      AlbumsScreen(),
      Color.Albums
    )

    val Artists = LibraryCategory(
      R.drawable.ic_microphone,
      R.string.Artists,
      ArtistsScreen(ArtistType.SongArtist),
      Color.Artists
    )

    val AlbumArtists = LibraryCategory(
      R.drawable.ic_account_box,
      R.string.AlbumArtists,
      ArtistsScreen(ArtistType.AlbumArtist),
      Color.AlbumArtists
    )

    val Genres = LibraryCategory(
      R.drawable.ic_guitar_acoustic,
      R.string.Genres,
      GenresScreen(),
      Color.Genres
    )

    val Composers = LibraryCategory(
      R.drawable.ic_person,
      R.string.Composers,
      ComposersScreen(),
      Color.Composers
    )

    val Playlists = LibraryCategory(
      R.drawable.ic_list,
      R.string.Playlists,
      PlaylistsScreen(),
      Color.Playlists
    )
  }
}

private class LibraryCategoriesImpl : LibraryCategories {
  private val categoryList = listOf(
    AllSongs,
    Albums,
    Artists,
    AlbumArtists,
    Genres,
    Composers,
    Playlists
  )

  override fun getItems(): List<LibraryCategory> = categoryList

  override fun get(key: ComposeKey): LibraryCategory =
    categoryList.find { it.key == key } ?: LibraryCategory.NullCategoryItem
}
