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
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.log._i
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.library.LocalAudioQueueOps.Op
import com.ealva.toque.ui.library.SongsViewModel.SongInfo
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.nav.goToScreen
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.annotation.concurrent.Immutable

private val LOG by lazyLogger(BaseSongsViewModel::class)

interface SongsViewModel : ActionsViewModel {
  @Immutable
  interface SongInfo : Parcelable {
    val id: MediaId
    val title: Title
    val duration: Millis
    val rating: Rating
    val album: AlbumTitle
    val artist: ArtistName
    val artwork: Uri

    companion object {
      operator fun invoke(
        id: MediaId,
        title: Title,
        duration: Millis,
        rating: Rating,
        album: AlbumTitle,
        artist: ArtistName,
        artwork: Uri
      ): SongInfo = SongInfoData(id, title, duration, rating, album, artist, artwork)

      @Immutable
      @Parcelize
      data class SongInfoData(
        override val id: MediaId,
        override val title: Title,
        override val duration: Millis,
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
}

abstract class BaseSongsViewModel(
  private val audioMediaDao: AudioMediaDao,
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : SongsViewModel, ScopedServices.Activated, ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private var requestJob: Job? = null
  private val localQueueOps = LocalAudioQueueOps(localAudioQueueModel)

  protected abstract val categoryToken: CategoryToken

  override val songsFlow = MutableStateFlow<List<SongInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<MediaId>(SelectedItems())
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(NoFilter)


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

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + dispatcher)
    filterFlow
      .drop(1)
      .onEach { requestAudio() }
      .launchIn(scope)

    audioMediaDao.audioDaoEvents
      .onStart { requestAudio() }
      .onEach { requestAudio() }
      .catch { cause -> LOG.e(cause) { it("Error collecting AudioDao events") } }
      .onCompletion { LOG._i { it("End collecting AudioDao events") } }
      .launchIn(scope)
  }

  /** Called whenever this view model is created and also whenever the dao emits an AudioDaoEvent */
  private fun requestAudio() {
    if (requestJob?.isActive == true) requestJob?.cancel()

    requestJob = scope.launch {
      getAudioList(audioMediaDao, filterFlow.value)
        .onFailure { cause ->
          LOG.e(cause) { it("Error getting audio list") }
          localAudioQueueModel.emitNotification(Notification(fetch(R.string.ErrorReadingMediaList)))
        }
        .onSuccess { list -> handleAudioList(list) }
    }
  }

  private fun handleAudioList(list: List<AudioDescription>) {
    songsFlow.value = list.mapIndexedTo(ArrayList(list.size)) { index, audioDescription ->
      makeSongInfo(index, audioDescription)
    }
  }

  protected open fun makeSongInfo(index: Int, it: AudioDescription) = SongInfo(
    id = it.mediaId,
    title = it.title,
    duration = it.duration,
    rating = it.rating,
    album = it.album,
    artist = it.artist,
    artwork = if (it.albumLocalArt !== Uri.EMPTY) it.albumLocalArt else it.albumArt
  )

  override fun mediaClicked(mediaId: MediaId) =
    selectedItems.ifInSelectionModeToggleElse(mediaId) {}

  override fun mediaLongClicked(mediaId: MediaId) = selectedItems.toggleSelection(mediaId)

  private fun makeMediaListResult(): Result<CategoryMediaList, Throwable> =
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

  private fun offSelectMode() {
    selectedItems.turnOffSelectionMode()
  }

  override fun play() {
    scope.launch { localQueueOps.doOp(Op.Play, ::makeMediaListResult, ::offSelectMode) }
  }

  override fun shuffle() {
    scope.launch { localQueueOps.doOp(Op.Shuffle, ::makeMediaListResult, ::offSelectMode) }
  }

  override fun playNext() {
    scope.launch { localQueueOps.doOp(Op.PlayNext, ::makeMediaListResult, ::offSelectMode) }
  }

  override fun addToUpNext() {
    scope.launch { localQueueOps.doOp(Op.AddToUpNext, ::makeMediaListResult, ::offSelectMode) }
  }

  override fun addToPlaylist() {
    scope.launch { localQueueOps.doOp(Op.AddToPlaylist, ::makeMediaListResult, ::offSelectMode) }
  }

  override fun displayMediaInfo() {
    val selected = selectedItems.value
    if (selected.selectedCount == 1) {
      val mediaId = selected.single()
      backstack.goToScreen(AudioMediaInfoScreen(mediaId))
    }
  }

  override fun onBackEvent(): Boolean = selectedItems.inSelectionModeThenTurnOff()

  /**
   * Defaults to the [javaClass] name of the implementation of this class and is used to save and
   * restore state
   */
  protected open val stateKey: String
    get() = javaClass.name

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(stateKey, SongsViewModelState(selectedItems.value))
  }

  override fun fromBundle(bundle: StateBundle?) {
    bundle?.getParcelable<SongsViewModelState>(stateKey)?.let {
      selectedItems.value = it.selected
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    songsFlow.value = emptyList()
  }
}

@Parcelize
private data class SongsViewModelState(
  val selected: SelectedItems<MediaId>
) : Parcelable

@Composable
fun SongsItemsActions(
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  viewModel: SongsViewModel
) {
  LibraryActionBar(
    itemCount = itemCount,
    inSelectionMode = selectedItems.inSelectionMode,
    selectedCount = selectedItems.selectedCount,
    play = { viewModel.play() },
    shuffle = { viewModel.shuffle() },
    playNext = { viewModel.playNext() },
    addToUpNext = { viewModel.addToUpNext() },
    addToPlaylist = { viewModel.addToPlaylist() },
    selectAllOrNone = { all -> if (all) viewModel.selectAll() else viewModel.clearSelection() },
    startSearch = {},
    selectActions = {
      SongSelectActions(
        selectedCount = selectedItems.selectedCount,
        mediaInfoClick = { viewModel.displayMediaInfo() }
      )
    }
  )
}

@Composable
fun SongSelectActions(
  selectedCount: Int,
  mediaInfoClick: () -> Unit
) {
  Row(
    modifier = Modifier
  ) {
    ActionButton(
      buttonHeight = 24.dp,
      modifier = Modifier.height(24.dp),
      drawable = R.drawable.ic_info,
      description = R.string.MediaInfo,
      onClick = mediaInfoClick,
      enabled = selectedCount == 1
    )
  }
}
