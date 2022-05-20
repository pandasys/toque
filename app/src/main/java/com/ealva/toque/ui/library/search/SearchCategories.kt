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

package com.ealva.toque.ui.library.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Duplicates
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.HasPersistentId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.ui.library.AlbumItem
import com.ealva.toque.ui.library.AlbumSongsScreen
import com.ealva.toque.ui.library.ArtistItem
import com.ealva.toque.ui.library.ArtistSongsScreen
import com.ealva.toque.ui.library.ArtistType
import com.ealva.toque.ui.library.ComposerItem
import com.ealva.toque.ui.library.ComposerSongsScreen
import com.ealva.toque.ui.library.GenreItem
import com.ealva.toque.ui.library.GenreSongsScreen
import com.ealva.toque.ui.library.LibraryCategories
import com.ealva.toque.ui.library.ListItemAlbumArtwork
import com.ealva.toque.ui.library.PlaylistItem
import com.ealva.toque.ui.library.PlaylistSongsScreen
import com.ealva.toque.ui.library.SelectedItems
import com.ealva.toque.ui.library.SongListItem
import com.ealva.toque.ui.library.data.AlbumInfo
import com.ealva.toque.ui.library.data.ArtistInfo
import com.ealva.toque.ui.library.data.ComposerInfo
import com.ealva.toque.ui.library.data.GenreInfo
import com.ealva.toque.ui.library.data.PlaylistInfo
import com.ealva.toque.ui.library.data.SongInfo
import com.ealva.toque.ui.library.data.makeCategoryMediaList
import com.ealva.toque.ui.library.formatSongsInfo
import com.ealva.toque.ui.library.hasSelection
import com.ealva.toque.ui.library.mapToAlbumInfo
import com.ealva.toque.ui.library.mapToArtistInfo
import com.ealva.toque.ui.library.mapToComposerInfo
import com.ealva.toque.ui.library.mapToGenreInfo
import com.ealva.toque.ui.library.mapToPlaylistInfo
import com.ealva.toque.ui.library.mapToSongInfo
import com.ealva.toque.ui.library.noSelection
import com.ealva.toque.ui.nav.goToScreen
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.zhuinden.simplestack.Backstack
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.parcelize.Parcelize

@Suppress("unused")
private val LOG by lazyLogger("SearchCategories")

@Parcelize
object AlbumsSearchCategory : SearchViewModel.SearchCategory<AlbumInfo, AlbumId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Albums

  @OptIn(ExperimentalFoundationApi::class)
  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<AlbumInfo, AlbumId>) -> Unit
  ): SearchViewModel.SearchResult<AlbumInfo, AlbumId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .albumDao
      .getAllAlbums(filter, limit)
      .mapToAlbumInfo(),
    item = { album, selected: SelectedItems<AlbumId>, result ->
      AlbumItem(
        albumInfo = album,
        isSelected = selected.isSelected(album.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(album, result, onChanged, backstack) { item ->
              AlbumSongsScreen(
                item.id,
                item.title,
                item.artwork,
                item.artist,
                formatSongsInfo(item.songCount, item.duration, item.year),
                fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(album.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<AlbumInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = list.makeCategoryMediaList(audioMediaDao)
}

@OptIn(ExperimentalFoundationApi::class)
@Parcelize
object ArtistsSearchCategory : SearchViewModel.SearchCategory<ArtistInfo, ArtistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Artists

  override val title: String
    get() = fetch(libraryCategory.title)

  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<ArtistInfo, ArtistId>) -> Unit
  ): SearchViewModel.SearchResult<ArtistInfo, ArtistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .artistDao
      .getSongArtists(filter, limit)
      .mapToArtistInfo(ArtistType.AlbumArtist, audioMediaDao),
    item = { artist, selected: SelectedItems<ArtistId>, result ->
      ArtistItem(
        artistInfo = artist,
        artistType = ArtistType.SongArtist,
        isSelected = selected.isSelected(artist.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(artist, result, onChanged, backstack) { item ->
              ArtistSongsScreen(
                artistId = item.id,
                artistType = ArtistType.SongArtist,
                artistName = item.name,
                artwork = item.artwork,
                backTo = fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(artist.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<ArtistInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = list.makeCategoryMediaList(audioMediaDao)
}

@OptIn(ExperimentalFoundationApi::class)
@Parcelize
object AlbumArtistsSearchCategory : SearchViewModel.SearchCategory<ArtistInfo, ArtistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.AlbumArtists

  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<ArtistInfo, ArtistId>) -> Unit
  ): SearchViewModel.SearchResult<ArtistInfo, ArtistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .artistDao
      .getAlbumArtists(filter, limit)
      .mapToArtistInfo(ArtistType.AlbumArtist, audioMediaDao),
    key = { it.id.value.toString() }, // differentiate from Artists SearchCategory
    item = { artist, selected: SelectedItems<ArtistId>, result ->
      ArtistItem(
        artistInfo = artist,
        artistType = ArtistType.AlbumArtist,
        isSelected = selected.isSelected(artist.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(artist, result, onChanged, backstack) { item ->
              ArtistSongsScreen(
                artistId = item.id,
                artistType = ArtistType.AlbumArtist,
                artistName = item.name,
                artwork = item.artwork,
                backTo = fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(artist.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<ArtistInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = list.makeCategoryMediaList(audioMediaDao)
}

@OptIn(ExperimentalFoundationApi::class)
@Parcelize
object GenresSearchCategory : SearchViewModel.SearchCategory<GenreInfo, GenreId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Genres

  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<GenreInfo, GenreId>) -> Unit
  ): SearchViewModel.SearchResult<GenreInfo, GenreId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .genreDao
      .getAllGenres(filter, limit)
      .mapToGenreInfo(audioMediaDao),
    item = { genre, selected: SelectedItems<GenreId>, result ->
      GenreItem(
        genreInfo = genre,
        isSelected = selected.isSelected(genre.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(genre, result, onChanged, backstack) { item ->
              GenreSongsScreen(
                genreId = item.id,
                genreName = item.name,
                artwork = item.artwork,
                backTo = fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(genre.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<GenreInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = list.makeCategoryMediaList(audioMediaDao)
}

@OptIn(ExperimentalFoundationApi::class)
@Parcelize
object ComposersSearchCategory : SearchViewModel.SearchCategory<ComposerInfo, ComposerId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Composers

  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<ComposerInfo, ComposerId>) -> Unit
  ): SearchViewModel.SearchResult<ComposerInfo, ComposerId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .composerDao
      .getAllComposers(filter, limit)
      .mapToComposerInfo(audioMediaDao),
    item = { composer, selected: SelectedItems<ComposerId>, result ->
      ComposerItem(
        composerInfo = composer,
        isSelected = selected.isSelected(composer.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(composer, result, onChanged, backstack) { item ->
              ComposerSongsScreen(
                composerId = item.id,
                composerName = item.name,
                artwork = item.artwork,
                backTo = fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(composer.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<ComposerInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = list.makeCategoryMediaList(audioMediaDao)
}

@OptIn(ExperimentalFoundationApi::class)
@Parcelize
object PlaylistsSearchCategory : SearchViewModel.SearchCategory<PlaylistInfo, PlaylistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Playlists

  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<PlaylistInfo, PlaylistId>) -> Unit
  ): SearchViewModel.SearchResult<PlaylistInfo, PlaylistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .playlistDao
      .getAllPlaylists(filter, limit)
      .mapToPlaylistInfo(audioMediaDao),
    item = { playlist, selected: SelectedItems<PlaylistId>, result ->
      PlaylistItem(
        playlistInfo = playlist,
        isSelected = selected.isSelected(playlist.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            selected.toggleElseGoTo(playlist, result, onChanged, backstack) { item ->
              PlaylistSongsScreen(
                playlistId = item.id,
                playListType = item.type,
                playlistName = item.name,
                artwork = item.artwork,
                backTo = fetch(R.string.Search)
              )
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(playlist.id))) }
        ),
        editSmartPlaylist = {},
        deletePlaylist = {}
      )
    }
  )

  /**
   * TODO consider passing [Duplicates] through to this function
   */
  override suspend fun makeCategoryMediaList(
    list: List<PlaylistInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> =
    list.makeCategoryMediaList(audioMediaDao, Duplicates(false))
}

@Parcelize
object SongsSearchCategory : SearchViewModel.SearchCategory<SongInfo, MediaId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.AllSongs

  @OptIn(ExperimentalFoundationApi::class)
  override suspend fun find(
    filter: Filter,
    limit: Limit,
    audioMediaDao: AudioMediaDao,
    backstack: Backstack,
    playMedia: (CategoryMediaList) -> Unit,
    onChanged: (SearchViewModel.SearchResult<SongInfo, MediaId>) -> Unit
  ): SearchViewModel.SearchResult<SongInfo, MediaId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .getAllAudio(filter, limit)
      .mapToSongInfo(
        onFailure = { cause -> LOG.e(cause) { it("Error getting songs %s %s", filter, limit) } }
      ),
    item = { song, selected: SelectedItems<MediaId>, result ->
      SongListItem(
        songInfo = song,
        highlightBackground = selected.isSelected(song.id),
        icon = { ListItemAlbumArtwork(artwork = song.artwork) },
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selected.inSelectionMode) {
              onChanged(result.copy(selected = selected.toggleSelection(song.id)))
            } else {
              playMedia(CategoryMediaList(MediaIdList(song.id), CategoryToken.All))
            }
          },
          onLongClick = { onChanged(result.copy(selected = selected.toggleSelection(song.id))) }
        ),
      )
    }
  )

  override suspend fun makeCategoryMediaList(
    list: List<SongInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> = runSuspendCatching {
    when {
      list.isEmpty() -> CategoryMediaList.EMPTY_ALL_LIST
      else -> CategoryMediaList(
        list.mapTo(LongArrayList(list.size)) { it.id.value }.asMediaIdList,
        CategoryToken.All
      )
    }
  }
}

/** If in selection mode, toggle the selected state of [item] and report the changed [result] via
 * [onChanged]. Otherwise, navigate via [backstack] to the screen created by [makeScreen]
 */
private inline fun <T : HasPersistentId<K>, K : PersistentId<K>> SelectedItems<K>.toggleElseGoTo(
  item: T,
  result: SearchResultImpl<T, K>,
  onChanged: (SearchViewModel.SearchResult<T, K>) -> Unit,
  backstack: Backstack,
  makeScreen: (T) -> ComposeKey
) = if (inSelectionMode) onChanged(result.copy(selected = toggleSelection(item.id.actual))) else
  backstack.goToScreen(makeScreen(item))

@Immutable
private data class SearchResultImpl<T : HasPersistentId<K>, K : PersistentId<K>>(
  private val audioMediaDao: AudioMediaDao,
  override val category: SearchViewModel.SearchCategory<T, K>,
  private val itemList: List<T>,
  private val key: (T) -> Any = { it.id },
  private val item: @Composable LazyListScope.(T, SelectedItems<K>, SearchResultImpl<T, K>) -> Unit,
  private val selected: SelectedItems<K> = SelectedItems()
) : SearchViewModel.SearchResult<T, K> {
  override val itemCount: Int get() = itemList.size
  override val inSelectionMode: Boolean get() = selected.inSelectionMode
  override val hasSelection: Boolean get() = selected.hasSelection
  override val selectedCount: Int get() = selected.selectedCount

  override fun isNotEmpty(): Boolean = itemList.isNotEmpty()
  override fun isSelected(item: T): Boolean = selected.isSelected(item.id.actual)
  override fun selectAll() = copy(selected = SelectedItems(getAllKeys()))
  override fun clearSelection() = copy(selected = selected.clearSelection())

  override fun setSelectionMode(mode: Boolean): SearchViewModel.SearchResult<T, K> =
    if (selected.inSelectionMode == mode) this else
      copy(selected = selected.toggleSelectionMode())

  override suspend fun getMediaList(
    selectedOnly: Boolean
  ): Result<CategoryMediaList, Throwable> = category.makeCategoryMediaList(
    list = if (selectedOnly) itemList.getSelected() else itemList,
    audioMediaDao = audioMediaDao
  )

  override fun items(lazyListScope: LazyListScope) {
    lazyListScope.items(itemList.size, key = { index -> key(itemList[index]) }) { index ->
      lazyListScope.item(itemList[index], selected, this@SearchResultImpl)
    }
  }

  private fun List<T>.getSelected(): List<T> = if (selected.noSelection) emptyList() else
    filter { item -> selected.isSelected(item.id.actual) }

  private fun getAllKeys() = itemList.mapTo(mutableSetOf()) { it.id.actual }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SearchResultImpl<*, *>) return false

    if (category != other.category) return false
    if (itemList != other.itemList) return false
    if (selected != other.selected) return false

    return true
  }

  override fun hashCode(): Int {
    var result = category.hashCode()
    result = 31 * result + itemList.hashCode()
    result = 31 * result + selected.hashCode()
    return result
  }
}
