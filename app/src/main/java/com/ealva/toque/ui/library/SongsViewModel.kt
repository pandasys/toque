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
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.db.AudioDescrResult
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoCommon.wrapAsFilter
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.ui.library.SongsViewModel.SongInfo
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
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

private val LOG by lazyLogger(BaseSongsViewModel::class)

interface SongsViewModel {
  @Parcelize
  data class SongInfo(
    val id: MediaId,
    val title: Title,
    val duration: Millis,
    val rating: Rating,
    val album: AlbumTitle,
    val artist: ArtistName,
    val artwork: Uri
  ) : Parcelable

  val allSongs: StateFlow<List<SongInfo>>
  val selectedItems: SelectedItemsFlow<MediaId>

  fun mediaClicked(mediaId: MediaId)
  fun mediaLongClicked(mediaId: MediaId)

  val searchFlow: StateFlow<String>
  fun setSearch(search: String)
}

abstract class BaseSongsViewModel(
  private val audioMediaDao: AudioMediaDao,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : SongsViewModel, ScopedServices.Activated, ScopedServices.HandlesBack, Bundleable {
  private lateinit var scope: CoroutineScope
  private var requestJob: Job? = null

  override val allSongs = MutableStateFlow<List<SongInfo>>(emptyList())
  override val selectedItems = SelectedItemsFlow<MediaId>(SelectedItems())
  override val searchFlow = MutableStateFlow("")
  private val filterFlow = MutableStateFlow(NoFilter)

  override fun setSearch(search: String) {
    searchFlow.value = search
    filterFlow.value = search.wrapAsFilter()
  }

  protected abstract suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): AudioDescrResult

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
      LOG._e { it("getAudioList") }
      when (val result = getAudioList(audioMediaDao, filterFlow.value)) {
        is Ok -> handleAudioList(result.value)
        is Err -> LOG.e { it("%s", result.error) }
      }
    }
  }

  private fun handleAudioList(list: List<AudioDescription>) {
    LOG._e { it("handleAudioList") }
    allSongs.value = list.mapTo(ArrayList(list.size)) {
      SongInfo(
        id = it.mediaId,
        title = it.title,
        duration = it.duration,
        rating = it.rating,
        album = it.album,
        artist = it.artist,
        artwork = if (it.albumLocalArt !== Uri.EMPTY) it.albumLocalArt else it.albumArt
      )
    }
  }

  override fun mediaClicked(mediaId: MediaId) = selectedItems.hasSelectionToggleElse(mediaId) {}

  override fun mediaLongClicked(mediaId: MediaId) = selectedItems.toggleSelection(mediaId)

  override fun onBackEvent(): Boolean = selectedItems.hasSelectionThenClear()

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
    allSongs.value = emptyList()
  }
}

@Parcelize
private data class SongsViewModelState(
  val selected: SelectedItems<MediaId>
) : Parcelable
