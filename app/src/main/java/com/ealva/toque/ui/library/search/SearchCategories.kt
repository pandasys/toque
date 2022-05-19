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
import com.ealva.toque.common.Duplicates
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.HasPersistentId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.ui.library.AlbumItem
import com.ealva.toque.ui.library.ArtistItem
import com.ealva.toque.ui.library.ArtistType
import com.ealva.toque.ui.library.ComposerItem
import com.ealva.toque.ui.library.GenreItem
import com.ealva.toque.ui.library.LibraryCategories
import com.ealva.toque.ui.library.ListItemAlbumArtwork
import com.ealva.toque.ui.library.PlaylistItem
import com.ealva.toque.ui.library.SelectedItems
import com.ealva.toque.ui.library.SongListItem
import com.ealva.toque.ui.library.data.AlbumInfo
import com.ealva.toque.ui.library.data.ArtistInfo
import com.ealva.toque.ui.library.data.ComposerInfo
import com.ealva.toque.ui.library.data.GenreInfo
import com.ealva.toque.ui.library.data.PlaylistInfo
import com.ealva.toque.ui.library.data.SongInfo
import com.ealva.toque.ui.library.data.makeCategoryMediaList
import com.ealva.toque.ui.library.mapToAlbumInfo
import com.ealva.toque.ui.library.mapToArtistInfo
import com.ealva.toque.ui.library.mapToComposerInfo
import com.ealva.toque.ui.library.mapToGenreInfo
import com.ealva.toque.ui.library.mapToPlaylistInfo
import com.ealva.toque.ui.library.mapToSongInfo
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.parcelize.Parcelize

@Suppress("unused")
private val LOG by lazyLogger("SearchCategories")

@Parcelize
object AlbumsSearchCategory : SearchModel.SearchCategory<AlbumInfo, AlbumId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Albums

  @OptIn(ExperimentalFoundationApi::class)
  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<AlbumInfo, AlbumId>) -> Unit
  ): SearchModel.SearchResult<AlbumInfo, AlbumId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .albumDao
      .getAllAlbums(filter, limit)
      .mapToAlbumInfo(),
    item = { albumInfo, selectedItems: SelectedItems<AlbumId>, searchResult ->
      AlbumItem(
        albumInfo = albumInfo,
        isSelected = selectedItems.isSelected(albumInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(albumInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(albumInfo.id))
            )
          }
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
object ArtistsSearchCategory : SearchModel.SearchCategory<ArtistInfo, ArtistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Artists

  override val title: String
    get() = fetch(libraryCategory.title)

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<ArtistInfo, ArtistId>) -> Unit
  ): SearchModel.SearchResult<ArtistInfo, ArtistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .artistDao
      .getSongArtists(filter, limit)
      .mapToArtistInfo(ArtistType.AlbumArtist, audioMediaDao),
    item = { artistInfo, selectedItems: SelectedItems<ArtistId>, searchResult ->
      ArtistItem(
        artistInfo = artistInfo,
        artistType = ArtistType.SongArtist,
        isSelected = selectedItems.isSelected(artistInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(artistInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(artistInfo.id))
            )
          }
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
object AlbumArtistsSearchCategory : SearchModel.SearchCategory<ArtistInfo, ArtistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.AlbumArtists

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<ArtistInfo, ArtistId>) -> Unit
  ): SearchModel.SearchResult<ArtistInfo, ArtistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .artistDao
      .getAlbumArtists(filter, limit)
      .mapToArtistInfo(ArtistType.AlbumArtist, audioMediaDao),
    key = { it.id.value.toString() }, // differentiate from Artists SearchCategory
    item = { artistInfo, selectedItems: SelectedItems<ArtistId>, searchResult ->
      ArtistItem(
        artistInfo = artistInfo,
        artistType = ArtistType.AlbumArtist,
        isSelected = selectedItems.isSelected(artistInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(artistInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(artistInfo.id))
            )
          }
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
object GenresSearchCategory : SearchModel.SearchCategory<GenreInfo, GenreId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Genres

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<GenreInfo, GenreId>) -> Unit
  ): SearchModel.SearchResult<GenreInfo, GenreId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .genreDao
      .getAllGenres(filter, limit)
      .mapToGenreInfo(audioMediaDao),
    item = { genreInfo, selectedItems: SelectedItems<GenreId>, searchResult ->
      GenreItem(
        genreInfo = genreInfo,
        isSelected = selectedItems.isSelected(genreInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(genreInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(genreInfo.id))
            )
          }
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
object ComposersSearchCategory : SearchModel.SearchCategory<ComposerInfo, ComposerId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Composers

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<ComposerInfo, ComposerId>) -> Unit
  ): SearchModel.SearchResult<ComposerInfo, ComposerId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .composerDao
      .getAllComposers(filter, limit)
      .mapToComposerInfo(audioMediaDao),
    item = { composerInfo, selectedItems: SelectedItems<ComposerId>, searchResult ->
      ComposerItem(
        composerInfo = composerInfo,
        isSelected = selectedItems.isSelected(composerInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(composerInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(composerInfo.id))
            )
          }
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
object PlaylistsSearchCategory : SearchModel.SearchCategory<PlaylistInfo, PlaylistId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.Playlists

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<PlaylistInfo, PlaylistId>) -> Unit
  ): SearchModel.SearchResult<PlaylistInfo, PlaylistId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .playlistDao
      .getAllPlaylists(filter, limit)
      .mapToPlaylistInfo(audioMediaDao),
    item = { playlistInfo, selectedItems: SelectedItems<PlaylistId>, searchResult ->
      PlaylistItem(
        playlistInfo = playlistInfo,
        isSelected = selectedItems.isSelected(playlistInfo.id),
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(playlistInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(playlistInfo.id))
            )
          }
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
object SongsSearchCategory : SearchModel.SearchCategory<SongInfo, MediaId> {
  override val libraryCategory: LibraryCategories.LibraryCategory
    get() = LibraryCategories.AllSongs

  @OptIn(ExperimentalFoundationApi::class)
  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchModel.SearchResult<SongInfo, MediaId>) -> Unit
  ): SearchModel.SearchResult<SongInfo, MediaId> = SearchResultImpl(
    audioMediaDao = audioMediaDao,
    category = this,
    itemList = if (filter.isBlank) emptyList() else audioMediaDao
      .getAllAudio(filter, limit)
      .mapToSongInfo(
        onFailure = { cause -> LOG.e(cause) { it("Error getting songs %s %s", filter, limit) } }
      ),
    item = { songInfo, selectedItems: SelectedItems<MediaId>, searchResult ->
      SongListItem(
        songInfo = songInfo,
        highlightBackground = selectedItems.isSelected(songInfo.id),
        icon = { ListItemAlbumArtwork(artwork = songInfo.artwork) },
        modifier = Modifier.combinedClickable(
          onClick = {
            if (selectedItems.inSelectionMode) {
              resultChanged(
                searchResult.copy(selectedItems = selectedItems.toggleSelection(songInfo.id))
              )
            }
          },
          onLongClick = {
            resultChanged(
              searchResult.copy(selectedItems = selectedItems.toggleSelection(songInfo.id))
            )
          }
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

/**
 * Would be nice to be able to have this class as package private so we could move these classes
 * into separate files
 */
@Immutable
private data class SearchResultImpl<T : HasPersistentId<K>, K : PersistentId<K>>(
  private val audioMediaDao: AudioMediaDao,
  override val category: SearchModel.SearchCategory<T, K>,
  private val itemList: List<T>,
  private val key: (T) -> Any = { it.id },
  private val item: @Composable LazyListScope.(T, SelectedItems<K>, SearchResultImpl<T, K>) -> Unit,
  private val selectedItems: SelectedItems<K> = SelectedItems()
) : SearchModel.SearchResult<T, K> {
  override val itemCount: Int
    get() = itemList.size

  override fun isNotEmpty(): Boolean = itemList.isNotEmpty()
  override fun items(lazyListScope: LazyListScope) {
    lazyListScope.items(itemList.size, key = { index -> key(itemList[index]) }) { index ->
      lazyListScope.item(itemList[index], selectedItems, this@SearchResultImpl)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SearchResultImpl<*, *>) return false

    if (category != other.category) return false
    if (itemList != other.itemList) return false
    if (selectedItems != other.selectedItems) return false

    return true
  }

  override fun hashCode(): Int {
    var result = category.hashCode()
    result = 31 * result + itemList.hashCode()
    result = 31 * result + selectedItems.hashCode()
    return result
  }

  override val inSelectionMode: Boolean
    get() = selectedItems.inSelectionMode

  override val hasSelection: Boolean
    get() = selectedItems.hasSelection

  override val selectedCount: Int
    get() = selectedItems.selectedCount

  override fun setSelectionMode(mode: Boolean): SearchModel.SearchResult<T, K> =
    if (selectedItems.inSelectionMode == mode) this else
      copy(selectedItems = selectedItems.toggleSelectionMode())

  override fun isSelected(item: T): Boolean {
    return selectedItems.isSelected(item.id.actual)
  }

  override suspend fun getMediaList(
    onlySelectedItems: Boolean
  ): Result<CategoryMediaList, Throwable> {
    LOG._e { it("getMediaIdList onlySelected:%s", onlySelectedItems) }
    val list = if (onlySelectedItems) {
      itemList.filter { item -> selectedItems.isSelected(item.id.actual) }
    } else itemList
    LOG._e { it("list.size:%d", list.size) }
    return category.makeCategoryMediaList(list, audioMediaDao)
  }

  private fun getAllKeys() = itemList.mapTo(mutableSetOf()) { it.id.actual }

  override fun selectAll(): SearchModel.SearchResult<T, K> = copy(
    selectedItems = SelectedItems(
      getAllKeys()
    )
  )

  override fun clearSelection(): SearchModel.SearchResult<T, K> =
    copy(selectedItems = selectedItems.clearSelection())
}
