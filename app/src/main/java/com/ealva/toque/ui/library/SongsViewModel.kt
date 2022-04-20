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

import android.net.Uri
import android.os.Parcelable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asMillis
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.cancelFlingOnBack
import com.ealva.toque.ui.library.SongsViewModel.SongInfo
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.nav.back
import com.ealva.toque.ui.nav.goToRootScreen
import com.ealva.toque.ui.nav.goToScreen
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.annotation.concurrent.Immutable
import kotlin.time.Duration

private val LOG by lazyLogger(BaseSongsViewModel::class)

interface SongsViewModel : ActionsViewModel {
  @Immutable
  interface SongInfo {
    val id: MediaId
    val title: Title
    val duration: Duration
    val rating: Rating
    val album: AlbumTitle
    val artist: ArtistName
    val artwork: Uri

    companion object {
      operator fun invoke(
        id: MediaId,
        title: Title,
        duration: Duration,
        rating: Rating,
        album: AlbumTitle,
        artist: ArtistName,
        artwork: Uri
      ): SongInfo = SongInfoData(id, title, duration, rating, album, artist, artwork)

      @Immutable
      data class SongInfoData(
        override val id: MediaId,
        override val title: Title,
        override val duration: Duration,
        override val rating: Rating,
        override val album: AlbumTitle,
        override val artist: ArtistName,
        override val artwork: Uri
      ) : SongInfo
    }
  }

  val songsFlow: StateFlow<List<SongInfo>>
  val selectedItems: SelectedItemsFlow<MediaId>

  fun mediaClicked(mediaId: MediaId)
  fun mediaLongClicked(mediaId: MediaId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)

  fun displayMediaInfo()

  fun goBack()
}

abstract class BaseSongsViewModel(
  private val audioMediaDao: AudioMediaDao,
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefs: AppPrefsSingleton,
  private val backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : SongsViewModel, ScopedServices.Registered, ScopedServices.Activated, ScopedServices.HandlesBack,
  Bundleable {

  protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private var requestJob: Job? = null
  private var daoEventsJob: Job? = null
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  protected abstract val categoryToken: CategoryToken

  override val songsFlow = MutableStateFlow<List<SongInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<MediaId>(SelectedItems())
  override val searchFlow = MutableStateFlow("")
  private val filterFlow: MutableStateFlow<Filter> = MutableStateFlow(NoFilter)


  override fun selectAll() = selectedItems.selectAll(getSongKeys())
  private fun getSongKeys(): Set<MediaId> = songsFlow.value.mapTo(mutableSetOf()) { it.id }
  override fun clearSelection() = selectedItems.clearSelection()

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  protected abstract suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable>

  override fun onServiceRegistered() {
    filterFlow
      .onEach { requestAudio() }
      .catch { cause -> LOG.e(cause) { it("Error in filterFlow for %s", javaClass) } }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    daoEventsJob = audioMediaDao.audioDaoEvents
      .onEach { requestAudio() }
      .catch { cause -> LOG.e(cause) { it("Error collecting AudioDao events") } }
      .onCompletion { LOG._i { it("End collecting AudioDao events") } }
      .launchIn(scope)
  }

  override fun onServiceInactive() {
    daoEventsJob?.cancel()
    daoEventsJob = null
  }

  /** Called whenever this view model is created and also whenever the dao emits an AudioDaoEvent */
  private fun requestAudio() {
    requestJob?.cancel()
    requestJob = scope.launch(Dispatchers.IO) {
      songsFlow.value = getAudioList(audioMediaDao, filterFlow.value)
        .onFailure { cause ->
          LOG.e(cause) { it("Error getting audio list") }
          localAudioQueueModel.emitNotification(Notification(fetch(R.string.ErrorReadingMediaList)))
        }
        .getOrElse { emptyList() }
        .mapIndexed { index, audioDescription -> makeSongInfo(index, audioDescription) }
    }
  }

  protected open fun makeSongInfo(index: Int, audio: AudioDescription) = SongInfo(
    id = audio.mediaId,
    title = audio.title,
    duration = audio.duration,
    rating = audio.rating,
    album = audio.album,
    artist = audio.artist,
    artwork = if (audio.localArtwork !== Uri.EMPTY) audio.localArtwork else audio.remoteArtwork
  )

  override fun mediaClicked(mediaId: MediaId) =
    selectedItems.ifInSelectionModeToggleElse(mediaId) {}

  override fun mediaLongClicked(mediaId: MediaId) = selectedItems.toggleSelection(mediaId)

  private fun getMediaList(): Result<CategoryMediaList, Throwable> =
    runSuspendCatching {
      CategoryMediaList(
        songsFlow
          .value
          .asSequence()
          .filterIfHasSelection(selectedItems.value) { it.id }
          .mapTo(LongArrayList(512)) { it.id.value }
          .asMediaIdList,
        categoryToken
      )
    }

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

  override fun displayMediaInfo() {
    val selected = selectedItems.value
    if (selected.selectedCount == 1) {
      songsFlow.value
        .find { info -> info.id == selected.single() }
        ?.let { info ->
          backstack.goToScreen(
            AudioMediaInfoScreen(
              info.id,
              info.title,
              info.album,
              info.artist,
              info.rating,
              info.duration.asMillis
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
    .cancelFlingOnBack(songsFlow)

  /**
   * Defaults to the [javaClass] name of the implementation of this class and is used to save and
   * restore state
   */
  protected open val stateKey: String
    get() = javaClass.name

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(stateKey, SongsViewModelState(selectedItems.value, searchFlow.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<SongsViewModelState>(stateKey)?.let {
      selectedItems.value = it.selected
      setSearch(it.search)
    }
  }
}

@Parcelize
private data class SongsViewModelState(
  val selected: SelectedItems<MediaId>,
  val search: String
) : Parcelable

@Composable
fun SongsItemsActions(
  modifier: Modifier = Modifier,
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  viewModel: SongsViewModel,
  buttonColors: ButtonColors
) {
  LibraryItemsActions(
    modifier = modifier,
    itemCount = itemCount,
    selectedItems = selectedItems,
    viewModel = viewModel,
    buttonColors = buttonColors,
    selectActions = {
      SongSelectActions(
        selectedCount = selectedItems.selectedCount,
        buttonColors = buttonColors,
        mediaInfoClick = { viewModel.displayMediaInfo() }
      )
    }
  )
}

@Composable
fun SongSelectActions(
  selectedCount: Int,
  buttonColors: ButtonColors,
  mediaInfoClick: () -> Unit
) {
  val buttonHeight = 24.dp
  val buttonModifier = Modifier
    .height(buttonHeight)
    .width(buttonHeight * 1.4F)
  Row(
    modifier = Modifier.padding(start = 4.dp)
  ) {
    ActionButton(
      modifier = buttonModifier,
      iconSize = buttonHeight,
      drawable = R.drawable.ic_info,
      description = R.string.MediaInfo,
      enabled = selectedCount == 1,
      colors = buttonColors,
      onClick = mediaInfoClick
    )
  }
}
