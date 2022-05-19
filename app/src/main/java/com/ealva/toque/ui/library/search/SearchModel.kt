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

import android.os.Parcelable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.SearchDao
import com.ealva.toque.db.SearchTerm
import com.ealva.toque.db.wildcard.SqliteLike.wrapAsFilter
import com.ealva.toque.persist.HasPersistentId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PersistentId
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.library.ActionsViewModel
import com.ealva.toque.ui.library.LibraryCategories.LibraryCategory
import com.ealva.toque.ui.library.LocalAudioQueueOps
import com.ealva.toque.ui.library.data.SongInfo
import com.ealva.toque.ui.library.search.SearchModel.SearchCategory
import com.ealva.toque.ui.library.search.SearchModel.SearchResult
import com.ealva.toque.ui.nav.backIfAllowed
import com.ealva.toque.ui.nav.goToRootScreen
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Suppress("unused")
private val LOG by lazyLogger(SearchModel::class)

interface SearchModel : ActionsViewModel {

  data class SearchState(
    val results: List<SearchResult<*, *>>,
    val query: TextFieldValue,
    val previousSearches: List<SearchTerm>,
    val inSelectionMode: Boolean,
    val selectedCount: Int
  ) {
    val totalItemCount: Int = results.sumOf { result -> result.itemCount }
  }

  val stateFlow: StateFlow<SearchState>
  val searchCategories: StateFlow<List<SearchCategory<*, *>>>
  val availableCategories: List<SearchCategory<*, *>>

  /**
   * Set the list of [categories] to search
   */
  fun setCategories(categories: List<LibraryCategory>)

  /**
   * Find every item for all [searchCategories] that matches [like]. The search [like] is
   * wrapped with wildcards so it will match any portion of the text being searched and is
   * case insensitive. See implementations of [SearchCategory] for any implementation details
   * specific to a category.
   *
   * * "a" will match "Will A Sky", "In a Out", "A", "a", "Stay"
   * * "da" and "DA" will match "David", "Mark David", "Monday"
   */
  fun search(like: TextFieldValue)

  fun deleteFromHistory(searchTerm: SearchTerm)

  fun goBack()

  fun <T : HasPersistentId<K>, K : PersistentId<K>> searchCategoryFor(
    libraryCategory: LibraryCategory
  ): SearchCategory<T, K>

  @Immutable
  interface SearchResult<T : HasPersistentId<K>, K : PersistentId<K>> {
    val category: SearchCategory<T, K>
    val itemCount: Int
    val inSelectionMode: Boolean
    val hasSelection: Boolean
    val selectedCount: Int
    fun isNotEmpty(): Boolean
    fun items(lazyListScope: LazyListScope)
    fun setSelectionMode(mode: Boolean): SearchResult<T, K>
    fun isSelected(item: T): Boolean
    suspend fun getMediaList(onlySelectedItems: Boolean): Result<CategoryMediaList, Throwable>
    fun selectAll(): SearchResult<T, K>
    fun clearSelection(): SearchResult<T, K>
  }

  @Immutable
  sealed interface SearchCategory<T : HasPersistentId<K>, K : PersistentId<K>> : Parcelable {
    val libraryCategory: LibraryCategory
    val title: String
      get() = fetch(libraryCategory.title)

    suspend fun find(
      audioMediaDao: AudioMediaDao,
      filter: Filter,
      limit: Limit = Limit.NoLimit,
      resultChanged: (SearchResult<T, K>) -> Unit = {}
    ): SearchResult<T, K>

    suspend fun makeCategoryMediaList(
      list: List<T>,
      audioMediaDao: AudioMediaDao
    ): Result<CategoryMediaList, Throwable>
  }

  companion object {
    operator fun invoke(
      audioMediaDao: AudioMediaDao,
      searchDao: SearchDao,
      localAudioQueue: LocalAudioQueueViewModel,
      goToNowPlaying: suspend () -> Boolean,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): SearchModel = SearchModelImpl(
      audioMediaDao = audioMediaDao,
      searchDao = searchDao,
      localAudioQueue = localAudioQueue,
      goToNowPlaying = goToNowPlaying,
      backstack = backstack,
      dispatcher = dispatcher
    )
  }
}

@Suppress("UNCHECKED_CAST")
private class SearchModelImpl(
  private val audioMediaDao: AudioMediaDao,
  searchDao: SearchDao,
  localAudioQueue: LocalAudioQueueViewModel,
  private val goToNowPlaying: suspend () -> Boolean,
  private val backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : SearchModel, ScopedServices.Registered, ScopedServices.HandlesBack,
  Bundleable {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueue)
  override val availableCategories: List<SearchCategory<*, *>> = listOf(
    AlbumsSearchCategory,
    ArtistsSearchCategory,
    AlbumArtistsSearchCategory,
    GenresSearchCategory,
    ComposersSearchCategory,
    PlaylistsSearchCategory,
    SongsSearchCategory,
  )

  private var searchJob: Job? = null
  private val filterFlow = MutableStateFlow(Filter.NoFilter)
  private val searchHistory: SearchHistory = SearchHistory(searchDao, dispatcher)
  private var didFirstSearch = false

  override val stateFlow = MutableStateFlow(
    SearchModel.SearchState(
      results = emptyList(),
      query = TextFieldValue(text = ""),
      previousSearches = emptyList(),
      inSelectionMode = false,
      selectedCount = 0
    )
  )
  override val searchCategories = MutableStateFlow(availableCategories)

  private val hasSelection: Boolean
    get() = stateFlow.value.results.any { searchResult -> searchResult.hasSelection }

  override fun onServiceRegistered() {
    searchCategories
      .drop(1)
      .onEach { categories -> doSearch(filterFlow.value, categories) }
      .catch { cause -> LOG.e(cause) { it("Error in searchCategories flow") } }
      .launchIn(scope)

    // Was debouncing this flow but currently don't see a need for it
    filterFlow
      .dropWhile { filter -> filter.isBlank }
      .onEach { filter -> doSearch(filter, searchCategories.value) }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow") } }
      .launchIn(scope)

    searchHistory.historyFlow
      .dropWhile { list -> list.isEmpty() }
      .onEach { list -> handleSearchHistory(list) }
      .launchIn(scope)
  }

  private fun handleSearchHistory(list: List<SearchTerm>) {
    if (!didFirstSearch && list.isNotEmpty()) {
      list.firstOrNull()?.let { term ->
        search(TextFieldValue(term.value, TextRange(0, term.value.length)))
      }
    }
    stateFlow.update { state -> state.copy(previousSearches = list) }
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun setCategories(categories: List<LibraryCategory>) {
    searchCategories.value = if (categories.isEmpty()) availableCategories else
      categories.map { libraryCategory ->
        availableCategories.first { it.libraryCategory == libraryCategory }
      }
  }

  override fun search(like: TextFieldValue) {
    stateFlow.update { state -> state.copy(query = like) }
    scope.launch {
      val likeText = like.text
      filterFlow.value = likeText.wrapAsFilter()
      if (like.text.isNotBlank()) {
        searchHistory.add(SearchTerm(likeText))
      }
    }
  }

  override fun deleteFromHistory(searchTerm: SearchTerm) {
    scope.launch {
      searchHistory.delete(searchTerm)
    }
  }

  override fun goBack() {
    backstack.backIfAllowed()
  }

  override fun <T : HasPersistentId<K>, K : PersistentId<K>> searchCategoryFor(
    libraryCategory: LibraryCategory
  ): SearchCategory<T, K> = availableCategories.first { searchCategory ->
    searchCategory.libraryCategory == libraryCategory
  } as SearchCategory<T, K>

  override fun selectAll() {
    stateFlow.update { state ->
      val results = state.results.map { result -> result.selectAll() }
      state.copy(
        results = results,
        inSelectionMode = true,
        selectedCount = results.selectedCount()
      )
    }
  }

  override fun clearSelection() {
    stateFlow.update { state ->
      state.copy(
        results = state.results.map { result -> result.clearSelection() },
        selectedCount = 0
      )
    }
  }

  override fun play() {
    scope.launch { localQueueOps.play(::getMediaList, ::selectModeOffMaybeGoHome) }
  }

  override fun shuffle() {
    scope.launch { localQueueOps.shuffle(::getMediaList, ::selectModeOffMaybeGoHome) }
  }

  override fun playNext() {
    scope.launch { localQueueOps.playNext(::getMediaList, ::selectModeOff) }
  }

  override fun addToUpNext() {
    scope.launch { localQueueOps.addToUpNext(::getMediaList, ::selectModeOff) }
  }

  override fun addToPlaylist() {
    scope.launch { localQueueOps.addToPlaylist(::getMediaList, ::selectModeOff) }
  }

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> {
    val onlySelectedItems = hasSelection
    return stateFlow.value.results
      .filter { searchResult -> !onlySelectedItems || searchResult.hasSelection }
      .map { searchResult -> searchResult.getMediaList(onlySelectedItems) }
      .onEach { result -> result.onFailure { cause -> LOG.e(cause) { it("Error Media ID") } } }
      .filter { result -> result is Ok && result.value.isNotEmpty }
      .reduceOrNull { acc, result -> acc.add(result) }
      ?.map { categoryMediaList -> categoryMediaList.removeDuplicates() }
      ?: Err(NoSuchElementException())
  }

  private fun selectModeOff() = turnOffSelectionMode()

  private suspend fun selectModeOffMaybeGoHome() {
    selectModeOff()
    if (goToNowPlaying()) backstack.goToRootScreen()
  }

  private fun cancelSearch() {
    searchJob?.cancel()
    searchJob = null
  }

  private fun doSearch(filter: Filter, categories: List<SearchCategory<*, *>>) {
    cancelSearch()
    didFirstSearch = true
    searchJob = categories
      .asFlow()
      .map { category -> category.find(audioMediaDao, filter, resultChanged = ::resultChanged) }
      .onEach { result -> handleCategoryResult(result) }
      .launchIn(scope)
  }

  private fun handleCategoryResult(result: SearchResult<*, *>) = stateFlow.update { state ->
    val results = state.results.replaceOrAdd(result)
    state.copy(results = results, selectedCount = results.selectedCount())
  }

  private fun resultChanged(newResult: SearchResult<*, *>) {
    val newSelectionMode = newResult.inSelectionMode && !stateFlow.value.inSelectionMode
    stateFlow.update { state ->
      val results = state.results.map { result ->
        when {
          result.category == newResult.category -> newResult
          newSelectionMode -> result.setSelectionMode(newSelectionMode)
          else -> result
        }
      }
      state.copy(
        results = results,
        inSelectionMode = if (newSelectionMode) newSelectionMode else state.inSelectionMode,
        selectedCount = results.selectedCount()
      )
    }
  }

  private fun List<SearchResult<*, *>>.selectedCount(): Int = sumOf { it.selectedCount }

  /**
   * If the a [SearchResult] with the same category as [result] exists in the list, replace it.
   * Otherwise, add it to the list.
   */
  private fun List<SearchResult<*, *>>.replaceOrAdd(
    result: SearchResult<*, *>
  ): List<SearchResult<*, *>> {
    var found = false
    val list = mapTo(mutableListOf<SearchResult<*, *>>()) { item ->
      if (item.category == result.category) {
        found = true
        result
      } else item
    }
    if (!found) list.add(result)
    return list
  }

  override fun onBackEvent(): Boolean = if (stateFlow.value.inSelectionMode) {
    turnOffSelectionMode()
    true
  } else false

  private fun turnOffSelectionMode() {
    stateFlow.update { state ->
      state.copy(
        results = state.results.map { result -> result.setSelectionMode(false) },
        inSelectionMode = false,
        selectedCount = 0
      )
    }
  }

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(KEY_PARCELIZED_STATE, ParcelizedState(searchCategories.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    if (bundle != null) {
      val state: ParcelizedState? = bundle.getParcelable(KEY_PARCELIZED_STATE)
      if (state != null) {
        searchCategories.value = state.list
      }
    }
  }
}

@Parcelize
private data class ParcelizedState(val list: List<SearchCategory<*, *>>) : Parcelable

private fun Result<CategoryMediaList, Throwable>.add(
  result: Result<CategoryMediaList, Throwable>
): Ok<CategoryMediaList> {
  val acc = getOrElse { CategoryMediaList.EMPTY_ALL_LIST }
  val addend = result.getOrElse { CategoryMediaList.EMPTY_ALL_LIST }
  val token = acc.token.takeIf { it != CategoryToken.All } ?: addend.token
  return Ok(CategoryMediaList(acc.idList + addend.idList, token))
}

private const val KEY_PARCELIZED_STATE = "SearchModelParcelizedState"

/**
 * For Test Only. Different module of test code can't implement the sealed interface. So I'll create
 * a Spy class here and R8 will strip it from the release version.
 */
@Suppress("PropertyName", "VariableNaming", "MemberVisibilityCanBePrivate")
@Parcelize
class SearchCategorySpy : SearchCategory<SongInfo, MediaId> {
  override val libraryCategory: LibraryCategory
    get() = LibraryCategory.NullCategoryItem

  @IgnoredOnParcel
  var _findCalled: Int = 0

  @IgnoredOnParcel
  var _findReturn: SearchResult<SongInfo, MediaId>? = null

  @IgnoredOnParcel
  var _findAudioMediaDao: AudioMediaDao? = null

  @IgnoredOnParcel
  var _findFilter: Filter? = null

  @IgnoredOnParcel
  var _findLimit: Limit? = null

  override suspend fun find(
    audioMediaDao: AudioMediaDao,
    filter: Filter,
    limit: Limit,
    resultChanged: (SearchResult<SongInfo, MediaId>) -> Unit
  ): SearchResult<SongInfo, MediaId> {
    _findCalled++
    _findAudioMediaDao = audioMediaDao
    _findFilter = filter
    _findLimit = limit
    return checkNotNull(_findReturn)
  }

  @IgnoredOnParcel
  var _makeCategoryMediaListCalled: Int = 0

  @IgnoredOnParcel
  var _makeCategoryMediaListReturn: Result<CategoryMediaList, Throwable> =
    Ok(CategoryMediaList.EMPTY_ALL_LIST)

  override suspend fun makeCategoryMediaList(
    list: List<SongInfo>,
    audioMediaDao: AudioMediaDao
  ): Result<CategoryMediaList, Throwable> {
    _makeCategoryMediaListCalled++
    return _makeCategoryMediaListReturn
  }
}
