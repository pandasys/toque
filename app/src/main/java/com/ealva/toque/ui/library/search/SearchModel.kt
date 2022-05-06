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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.ui.library.AlbumsViewModel.AlbumInfo
import com.ealva.toque.ui.library.ArtistType
import com.ealva.toque.ui.library.ArtistsViewModel.ArtistInfo
import com.ealva.toque.ui.library.ComposersViewModel.ComposerInfo
import com.ealva.toque.ui.library.GenresViewModel.GenreInfo
import com.ealva.toque.ui.library.PlaylistsViewModel.PlaylistInfo
import com.ealva.toque.ui.library.SongsViewModel.SongInfo
import com.ealva.toque.ui.library.mapToAlbumInfo
import com.ealva.toque.ui.library.mapToArtistInfo
import com.ealva.toque.ui.library.mapToComposerInfo
import com.ealva.toque.ui.library.mapToGenreInfo
import com.ealva.toque.ui.library.mapToPlaylistInfo
import com.ealva.toque.ui.library.mapToSongInfo
import com.ealva.toque.ui.library.search.SearchModel.SearchCategory.Companion.available
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableList

@Suppress("unused")
private val LOG by lazyLogger(SearchModel::class)

interface SearchModel {
  data class SearchResult<T>(val category: SearchCategory<T>, val list: List<T>)

  sealed interface SearchCategory<T> {
    /**
     * Find each category item that matches [filter] and [limit] the number of items. Each category
     * uses wildcard matching to find items "like" the filter. If the filter contains wildcard
     * characters they are escaped to match the specific character.
     */
    suspend fun find(
      audioMediaDao: AudioMediaDao,
      filter: Filter,
      limit: Limit = Limit.NoLimit
    ): SearchResult<T>

    object Songs : SearchCategory<SongInfo> {
      /**
       * Find each song where the [filter] matches the [SongInfo.title], [SongInfo.album], or
       * [SongInfo.artist]
       */
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<SongInfo> = SearchResult(
        this,
        audioMediaDao
          .getAllAudio(filter, limit)
          .mapToSongInfo(
            onFailure = { cause -> LOG.e(cause) { it("Error getting songs %s %s", filter, limit) } }
          )
      )
    }

    object Albums : SearchCategory<AlbumInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<AlbumInfo> = SearchResult(
        this,
        audioMediaDao
          .albumDao
          .getAllAlbums(filter, limit)
          .mapToAlbumInfo()
      )
    }

    object Artists : SearchCategory<ArtistInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<ArtistInfo> = SearchResult(
        this,
        audioMediaDao
          .artistDao
          .getSongArtists(filter, limit)
          .mapToArtistInfo(ArtistType.SongArtist, audioMediaDao)
      )
    }

    object AlbumArtists : SearchCategory<ArtistInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<ArtistInfo> = SearchResult(
        this,
        audioMediaDao
          .artistDao
          .getAlbumArtists(filter, limit)
          .mapToArtistInfo(ArtistType.AlbumArtist, audioMediaDao)
      )
    }

    object Genres : SearchCategory<GenreInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<GenreInfo> = SearchResult(
        this,
        audioMediaDao
          .genreDao
          .getAllGenres(filter, limit)
          .mapToGenreInfo(audioMediaDao)
      )
    }

    object Composers : SearchCategory<ComposerInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<ComposerInfo> = SearchResult(
        this,
        audioMediaDao
          .composerDao
          .getAllComposers(filter, limit)
          .mapToComposerInfo(audioMediaDao)
      )
    }

    object Playlists : SearchCategory<PlaylistInfo> {
      override suspend fun find(
        audioMediaDao: AudioMediaDao,
        filter: Filter,
        limit: Limit
      ): SearchResult<PlaylistInfo> = SearchResult(
        this,
        audioMediaDao
          .playlistDao
          .getAllPlaylists(filter, limit)
          .mapToPlaylistInfo(audioMediaDao)
      )
    }

    companion object {
      val available: List<SearchCategory<*>> = listOf(
        Songs,
        Albums,
        Artists,
        AlbumArtists,
        Genres,
        Composers,
        Playlists,
      )
    }
  }

  val searchResults: StateFlow<List<SearchResult<*>>>
  val searchFlow: StateFlow<String>
  val searchCategories: StateFlow<List<SearchCategory<*>>>

  /**
   * Set the list of [categories] to search
   */
  fun setCategories(categories: List<SearchCategory<*>>)

  /**
   * Find every item for all [searchCategories] that matches [like]. The search [like] is
   * wrapped with wildcards so it will match any portion of the text being searched and is
   * case insensitive. See implementations of [SearchCategory] for any implementation details
   * specific to a category.
   *
   * * "a" will match "Will A Sky", "In a Out", "A", "a", "Stay"
   * * "da" and "DA" will match "David", "Mark David", "Bandaid"
   */
  fun search(like: String)

  companion object {
    operator fun invoke(
      audioMediaDao: AudioMediaDao,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): SearchModel = SearchModelImpl(audioMediaDao, dispatcher)
  }
}

private class SearchModelImpl(
  private val audioMediaDao: AudioMediaDao,
  dispatcher: CoroutineDispatcher
) : SearchModel, ScopedServices.Registered {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private var searchJob: Job? = null
  override val searchResults = MutableStateFlow<List<SearchModel.SearchResult<*>>>(emptyList())
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  override val searchCategories = MutableStateFlow(available)

  @OptIn(FlowPreview::class)
  override fun onServiceRegistered() {
    LOG.e { it("onServiceRegistered") }
    // Drop the first because we'll do first search on call to search() and we're going to
    // debounce this flow, but don't want to delay first search
    filterFlow
      .drop(1)
      .debounce(Filter.debounceDuration)
      .onEach { filter -> doSearch(filter, searchCategories.value) }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow") } }
      .launchIn(scope)

    searchCategories
      .drop(1)
      .onEach { categories -> doSearch(filterFlow.value, categories) }
      .catch { cause -> LOG.e(cause) { it("Error in searchCategories flow") } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun setCategories(categories: List<SearchModel.SearchCategory<*>>) {
    searchCategories.value = (categories.takeIf { it.isNotEmpty() } ?: available).toImmutableList()
  }

  override fun search(like: String) {
    searchFlow.value = like
    filterFlow.value = like.wrapAsFilter()
  }

  private fun cancelSearch() {
    searchJob?.cancel()
    searchJob = null
    searchResults.value = emptyList()
  }

  @OptIn(FlowPreview::class)
  private fun doSearch(filter: Filter, categories: List<SearchModel.SearchCategory<*>>) {
    cancelSearch()
    if (filter.value.length > 1) {
      searchJob = scope.launch {
        categories
          .asFlow()
          .map { category -> async { category.find(audioMediaDao, filter) } }
          .buffer(3)
          .map { deferred -> deferred.await() }
          .onEach { result -> searchResults.update { list -> list + result } }
          .catch { cause -> LOG.e(cause) { it("Error processing search flow") } }
          .onCompletion { searchJob = null }
          .collect()
      }
    }
  }
}

/**
 * For Test Only. Because of the way I structured Android test packages I can't implement the sealed
 * interface in the test code. So I'll create a Stub class here and R8 will strip it from the
 * release version.
 */
@Suppress("PropertyName", "VariableNaming", "MemberVisibilityCanBePrivate")
class SearchCategorySpy : SearchModel.SearchCategory<SongInfo> {
  var _findCalled: Int = 0
  var _findReturn: SearchModel.SearchResult<SongInfo>? = null
  var _findAudioMediaDao: AudioMediaDao? = null
  var _findFilter: Filter? = null
  var _findLimit: Limit? = null
  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit
  ): SearchModel.SearchResult<SongInfo> {
    _findCalled++
    _findAudioMediaDao = audioMediaDao
    _findFilter = filter
    _findLimit = limit
    return checkNotNull(_findReturn)
  }
}
