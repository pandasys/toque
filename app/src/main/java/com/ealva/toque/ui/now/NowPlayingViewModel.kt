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

package com.ealva.toque.ui.now

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Rating
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.asDurationString
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.StreamVolume
import com.ealva.toque.ui.art.SelectAlbumArtScreen
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.library.AlbumSongsScreen
import com.ealva.toque.ui.library.ArtistSongsScreen
import com.ealva.toque.ui.library.ArtistType
import com.ealva.toque.ui.library.AudioMediaInfoScreen
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.now.NowPlayingViewModel.NowPlayingState
import com.ealva.toque.ui.now.NowPlayingViewModel.QueueItem
import com.ealva.toque.ui.now.NowPlayingViewModel.QueueItem.Companion.NullQueueItem
import com.github.michaelbull.result.onSuccess
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.annotation.concurrent.Immutable

interface NowPlayingViewModel {
  @Immutable
  data class QueueItem(
    val id: MediaId,
    val title: Title,
    val albumTitle: AlbumTitle,
    val albumArtist: ArtistName,
    val artist: ArtistName,
    val duration: Millis,
    val trackNumber: Int,
    val localAlbumArt: Uri,
    val albumArt: Uri,
    val rating: Rating,
    val isValid: Boolean
  ) {
    companion object {
      val NullQueueItem by lazy {
        QueueItem(
          id = MediaId.INVALID,
          title = Title.EMPTY,
          albumTitle = AlbumTitle(""),
          albumArtist = ArtistName(""),
          artist = ArtistName(""),
          duration = Millis(0),
          trackNumber = 0,
          localAlbumArt = Uri.EMPTY,
          albumArt = Uri.EMPTY,
          rating = Rating.RATING_NONE,
          isValid = false
        )
      }
    }
  }

  @Immutable
  data class NowPlayingState(
    val queue: List<QueueItem>,
    val queueIndex: Int,
    val position: Millis,
    val duration: Millis,
    val playingState: PlayState,
    val repeatMode: RepeatMode,
    val shuffleMode: ShuffleMode,
    val eqMode: EqMode,
    val currentPreset: EqPreset,
    val extraMediaInfo: String,
    val playbackRate: PlaybackRate,
    val showTimeRemaining: Boolean = false
  ) {
    val currentItem: QueueItem
      get() = if (queueIndex in queue.indices) queue[queueIndex] else NullQueueItem

    fun getDurationDisplay(): String =
      (if (showTimeRemaining) position - duration else duration).asDurationString

    fun getPositionDisplay(): String = position.asDurationString

    companion object {
      val NONE = NowPlayingState(
        queue = emptyList(),
        queueIndex = -1,
        position = Millis(0),
        duration = Millis(0),
        playingState = PlayState.Stopped,
        repeatMode = RepeatMode.None,
        shuffleMode = ShuffleMode.None,
        eqMode = EqMode.Off,
        currentPreset = EqPreset.NONE,
        extraMediaInfo = "",
        playbackRate = PlaybackRate.NORMAL
      )
    }
  }


  val nowPlayingState: StateFlow<NowPlayingState>

  fun nextShuffleMode()
  fun nextMedia()
  fun previousMedia()
  fun nextList()
  fun previousList()
  fun togglePlayPause()
  fun nextRepeatMode()
  fun toggleEqMode()
  fun seekTo(position: Millis)
  fun goToQueueIndexMaybePlay(index: Int)

  /**
   * Schedule a sleep timer with the given duration. If one is already scheduled it is cancelled
   * before scheduling the new timer. If duration is Duration.ZERO_LENGTH or not greater than zero,
   * any currently scheduled timer is cancelled.
   *
   * @param duration duration of the sleep timer, or [Millis](0) to cancel an already scheduled
   * timer
   */
  fun scheduleSleepTimer(duration: Millis)

  /**
   * Get the time remaining for a scheduled sleep timer. If Duration = zero, no timer is scheduled
   *
   * @return the time remaining or no timer if Duration milliseconds == 0
   */
  val remainingSleepTimer: Millis

  val streamVolume: StreamVolume

  fun toggleShowTimeRemaining()

  fun showCurrentItemDialog()

  companion object {
    operator fun invoke(
      backstack: Backstack,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      appPrefsSingleton: AppPrefsSingleton
    ): NowPlayingViewModel =
      NowPlayingViewModelImpl(
        backstack,
        audioMediaDao,
        localAudioQueueModel,
        appPrefsSingleton
      )
  }
}

private val LOG by lazyLogger(NowPlayingViewModelImpl::class)

/**
 * This view model does not save it's state as that information is already saved in the
 * MediaPlayerService. When we connect we will receive the state information.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class NowPlayingViewModelImpl(
  private val backstack: Backstack,
  private val audioMediaDao: AudioMediaDao,
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val appPrefsSingleton: AppPrefsSingleton
) : NowPlayingViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

  private val currentItem: QueueItem get() = nowPlayingState.value.currentItem

  private suspend fun appPrefs(): AppPrefs = appPrefsSingleton.instance()

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
      appPrefs().showTimeRemaining
        .asFlow()
        .onEach { remaining -> nowPlayingState.update { it.copy(showTimeRemaining = remaining) } }
        .launchIn(scope)
    }


    currentQueueJob = localAudioQueueModel.localAudioQueue
      .onEach { queue -> handleQueueChange(queue) }
      .launchIn(scope)
  }

  override fun onServiceInactive() {
    currentQueueJob?.cancel()
    currentQueueJob = null
    handleQueueChange(NullLocalAudioQueue)
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    when (queue) {
      is NullLocalAudioQueue -> queueInactive()
      is LocalAudioQueue -> queueActive(queue)
      else -> queueInactive()
    }
  }

  private fun queueActive(queue: LocalAudioQueue) {
    audioQueue = queue
    queueStateJob = audioQueue.queueState
      .onEach { state -> handleServiceState(state) }
      .catch { cause -> LOG.e(cause) { it("") } }
      .onCompletion { LOG._i { it("LocalAudioQueue state flow completed") } }
      .launchIn(scope)
  }

  private fun queueInactive() {
    queueStateJob?.cancel()
    queueStateJob = null
    audioQueue = NullLocalAudioQueue
  }

  private fun handleServiceState(queueState: LocalAudioQueueState) = nowPlayingState.update {
    it.copy(
      queue = queueState.queue.toNowPlayingList(),
      queueIndex = queueState.queueIndex,
      position = queueState.position,
      duration = queueState.duration,
      playingState = queueState.playingState,
      repeatMode = queueState.repeatMode,
      shuffleMode = queueState.shuffleMode,
      eqMode = queueState.eqMode,
      currentPreset = queueState.currentPreset,
      extraMediaInfo = queueState.extraMediaInfo,
      playbackRate = queueState.playbackRate
    )
  }

  override fun nextShuffleMode() = audioQueue.nextShuffleMode()

  override fun nextMedia() = audioQueue.next()

  override fun previousMedia() = audioQueue.previous()

  override fun nextList() = audioQueue.nextList()

  override fun previousList() = audioQueue.previousList()

  override fun togglePlayPause() = audioQueue.togglePlayPause()

  override fun nextRepeatMode() = audioQueue.nextRepeatMode()

  override fun toggleEqMode() = audioQueue.toggleEqMode()

  override fun seekTo(position: Millis) = audioQueue.seekTo(position)

  override fun goToQueueIndexMaybePlay(index: Int) = audioQueue.goToIndexMaybePlay(index)

  override fun scheduleSleepTimer(duration: Millis) {
    TODO("Not yet implemented")
  }

  override val remainingSleepTimer: Millis
    get() = TODO("Not yet implemented")

  override val streamVolume: StreamVolume
    get() = TODO()

  override fun toggleShowTimeRemaining() {
    scope.launch {
      appPrefs().edit { store ->
        store[showTimeRemaining] = !showTimeRemaining()
      }
    }
  }

  private fun displayMediaInfo(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    backstack.goToScreen(AudioMediaInfoScreen(audioItem.id))
  }

  private fun selectAlbumArt(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    backstack.goToScreen(SelectAlbumArtScreen(audioItem.id, audioItem.albumTitle, audioItem.artist))
  }

  private fun goToAlbum(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    scope.launch {
      audioMediaDao
        .getAlbumId(audioItem.id)
        .onSuccess { albumId -> backstack.goToScreen(AlbumSongsScreen(albumId)) }

    }
  }

  private fun goToArtist(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    scope.launch {
      audioMediaDao
        .getArtistId(audioItem.id)
        .onSuccess { artistId ->
          backstack.goToScreen(ArtistSongsScreen(artistId, ArtistType.SongArtist))
        }
    }
  }

  override fun showCurrentItemDialog() {
    currentItem.let { item ->
      if (item.isValid)
        localAudioQueueModel.showPrompt(
          DialogPrompt(
            prompt = {
              CurrentItemDialog(
                audioItem = item,
                onDismiss = { localAudioQueueModel.clearPrompt() },
                showMediaInfo = ::displayMediaInfo,
                selectAlbumArt = ::selectAlbumArt,
                goToAlbum = ::goToAlbum,
                goToArtist = ::goToArtist
              )
            }
          )
        )
    }
  }
}

private fun List<AudioItem>.toNowPlayingList(): List<QueueItem> = map { it.toQueueItem }

private inline val AudioItem.toQueueItem: QueueItem
  get() = QueueItem(
    id,
    title,
    albumTitle,
    albumArtist,
    artist,
    duration,
    trackNumber,
    localAlbumArt,
    albumArt,
    rating,
    isValid
  )

