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

import android.os.Parcelable
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.preferredArt
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.db.DaoResult
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.art.SelectAlbumArtScreen
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.library.AlbumsViewModel.AlbumInfo
import com.ealva.toque.ui.nav.back
import com.ealva.toque.ui.nav.goToRootScreen
import com.ealva.toque.ui.nav.goToScreen
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(BaseAlbumsViewModel::class)

abstract class BaseAlbumsViewModel(
  private val albumDao: AlbumDao,
  protected val backstack: Backstack,
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefs: AppPrefsSingleton,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : AlbumsViewModel, ScopedServices.Registered, ScopedServices.Activated,
  ScopedServices.HandlesBack, Bundleable {
  protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private var wentInactive = false

  override val albumFlow = MutableStateFlow<List<AlbumInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<AlbumId>()
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(NoFilter)
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  override fun onServiceRegistered() {
    filterFlow
      .onEach { requestAlbums() }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow for %s", javaClass) } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    daoEventsJob = albumDao.albumDaoEvents
      .onEach { requestAlbums() }
      .catch { cause -> LOG.e(cause) { it("Error collecting AlbumDao events") } }
      .onCompletion { LOG._i { it("End collecting AlbumDao events") } }
      .launchIn(scope)

    if (wentInactive) {
      wentInactive = false
      requestAlbums()
    }
  }

  override fun onServiceInactive() {
    daoEventsJob?.cancel()
    daoEventsJob = null
    wentInactive = true
  }

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  protected abstract fun goToAlbumSongs(album: AlbumInfo)

  /** Called whenever this view model becomes active and whenever AlbumDao emits an event */
  private fun requestAlbums() {
    if (requestJob?.isActive == true) requestJob?.cancel()
    requestJob = scope.launch {
      albumFlow.value = doGetAlbums(albumDao, filterFlow.value)
        .onFailure { cause -> LOG.e(cause) { it("Error getting Albums") } }
        .getOrElse { emptyList() }
        .map { album -> album.asAlbumInfo }
    }
  }

  private inline val AlbumDescription.asAlbumInfo: AlbumInfo
    get() = AlbumInfo(
      id = albumId,
      title = albumTitle,
      artist = artistName,
      year = albumYear,
      artwork = preferredArt,
      songCount = songCount.toInt(),
      duration = duration
    )

  protected abstract suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): DaoResult<List<AlbumDescription>>

  override fun itemClicked(album: AlbumInfo) = selectedItems.ifInSelectionModeToggleElse(album.id) {
    goToAlbumSongs(album)
  }

  override fun itemLongClicked(album: AlbumInfo) = selectedItems.toggleSelection(album.id)

  override fun selectAlbumArt() {
    localAudioQueueModel.clearPrompt()
    val selected = selectedItems.value
    if (selected.selectedCount == 1) {
      albumFlow.value
        .find { album -> selected.isSelected(album.id) }
        ?.let { album ->
          backstack.goToScreen(
            SelectAlbumArtScreen(
              album.id,
              album.title,
              album.artist
            )
          )
        }
    }
  }

  override fun goBack() {
    selectedItems.inSelectionModeThenTurnOff()
    backstack.back()
  }

  override fun onBackEvent(): Boolean = selectedItems
    .inSelectionModeThenTurnOff()
    .cancelFlingOnBack(albumFlow)

  protected open val stateKey: String
    get() = javaClass.name

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(stateKey, AlbumsViewModelState(selectedItems.value, searchFlow.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<AlbumsViewModelState>(stateKey)?.let { modelState ->
      selectedItems.value = modelState.selected
      setSearch(modelState.search)
    }
  }

  override fun selectAll() = selectedItems.selectAll(getAlbumKeys())
  private fun getAlbumKeys() = albumFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  protected abstract suspend fun makeCategoryMediaList(
    albumList: List<AlbumInfo>
  ): Result<CategoryMediaList, Throwable>

  private suspend fun getMediaList(): Result<CategoryMediaList, Throwable> =
    makeCategoryMediaList(getSelectedAlbums())

  private fun selectModeOff() = selectedItems.turnOffSelectionMode()

  private suspend fun selectModeOffMaybeGoHome() {
    selectModeOff()
    if (appPrefs.instance().goToNowPlaying()) backstack.goToRootScreen()
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

  /** Get selected albums, or all if no selection */
  private fun getSelectedAlbums() = albumFlow.value
    .filterIfHasSelection(selectedItems.value) { it.id }
}

@Parcelize
private data class AlbumsViewModelState(
  val selected: SelectedItems<AlbumId>,
  val search: String
) : Parcelable
