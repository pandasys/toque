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
import com.ealva.toque.R
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Rating
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.asDurationString
import com.ealva.toque.common.asMillis
import com.ealva.toque.common.debugRequire
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
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
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.annotation.concurrent.Immutable
import kotlin.time.Duration

interface NowPlayingViewModel {
  @Immutable
  data class QueueItem(
    val id: MediaId,
    val title: Title,
    val albumTitle: AlbumTitle,
    val albumArtist: ArtistName,
    val artist: ArtistName,
    val duration: Duration,
    val trackNumber: Int,
    val localAlbumArt: Uri,
    val albumArt: Uri,
    val rating: Rating,
    val albumId: AlbumId
  ) {
    val artwork: Uri
      get() = if (localAlbumArt !== Uri.EMPTY) localAlbumArt else albumArt

    companion object {
      val NullQueueItem by lazy {
        QueueItem(
          id = MediaId.INVALID,
          title = Title(""),
          albumTitle = AlbumTitle(""),
          albumArtist = ArtistName(""),
          artist = ArtistName(""),
          duration = Duration.ZERO,
          trackNumber = 0,
          localAlbumArt = Uri.EMPTY,
          albumArt = Uri.EMPTY,
          rating = Rating.RATING_NONE,
          albumId = AlbumId.INVALID,
        )
      }
    }
  }

  @Immutable
  data class NowPlayingState(
    val queue: List<QueueItem>,
    val queueIndex: Int,
    val position: Millis,
    val duration: Duration,
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
      (if (showTimeRemaining) position.toDuration() - duration else duration).asDurationString

    fun getPositionDisplay(): String = position.toDuration().asDurationString

    companion object {
      val NONE = NowPlayingState(
        queue = emptyList(),
        queueIndex = -1,
        position = Millis.ZERO,
        duration = Duration.ZERO,
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

  /**
   * Called when the user is seeking using a scrubber (typically slider). This puts the
   * audio into a "user seeking" mode. [userSeekingComplete] must be called when the user
   * is finished seeking
   */
  fun seekTo(position: Millis)

  /**
   * Called when the user is finished seeking within media
   */
  fun userSeekingComplete()
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
) : NowPlayingViewModel, ScopedServices.Registered {
  private lateinit var scope: CoroutineScope
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

  private val currentItem: QueueItem get() = nowPlayingState.value.currentItem
  private val currentIndex: Int get() = nowPlayingState.value.queueIndex

  private suspend fun appPrefs(): AppPrefs = appPrefsSingleton.instance()

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope.launch {
      appPrefs().showTimeRemaining
        .asFlow()
        .onEach { remaining -> nowPlayingState.update { it.copy(showTimeRemaining = remaining) } }
        .collect()
    }


    currentQueueJob = localAudioQueueModel.localAudioQueue
      .onEach { queue -> handleQueueChange(queue) }
      .launchIn(scope)
  }

  override fun onServiceUnregistered() {
//    currentQueueJob?.cancel()
//    currentQueueJob = null
//    handleQueueChange(NullLocalAudioQueue)
    scope.cancel()
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

  private fun handleServiceState(newState: LocalAudioQueueState) = nowPlayingState.update { state ->
    state.copy(
      queue = newState.maybeNewQueue(state),
      queueIndex = newState.queueIndex,
      position = newState.position,
      duration = newState.duration,
      playingState = newState.playingState,
      repeatMode = newState.repeatMode,
      shuffleMode = newState.shuffleMode,
      eqMode = newState.eqMode,
      currentPreset = newState.currentPreset,
      extraMediaInfo = newState.extraMediaInfo,
      playbackRate = newState.playbackRate
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
//    nowPlayingState.update { state -> state.copy(position = position) }

  override fun userSeekingComplete() = audioQueue.seekTo(nowPlayingState.value.position)

  override fun goToQueueIndexMaybePlay(index: Int) {
    if (index != currentIndex) {
      audioQueue.goToIndexMaybePlay(index)
    }
  }

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
    backstack.goToScreen(
      AudioMediaInfoScreen(
        audioItem.id,
        audioItem.title,
        audioItem.albumTitle,
        audioItem.albumArtist,
        audioItem.rating,
        audioItem.duration.asMillis
      )
    )
  }

  private fun selectAlbumArt(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    backstack.goToScreen(
      SelectAlbumArtScreen(audioItem.albumId, audioItem.albumTitle, audioItem.artist)
    )
  }

  private fun goToAlbum(item: QueueItem) {
    localAudioQueueModel.clearPrompt()
    backstack.goToScreen(
      AlbumSongsScreen(
        item.albumId,
        item.albumTitle,
        item.artwork,
        item.albumArtist,
        fetch(R.string.NowPlaying)
      )
    )
  }

  private fun goToArtist(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    scope.launch {
      audioMediaDao
        .getArtistId(audioItem.id)
        .onSuccess { artistId ->
          backstack.goToScreen(
            ArtistSongsScreen(
              artistId = artistId,
              artistType = ArtistType.SongArtist,
              artistName = audioItem.artist,
              artwork = Uri.EMPTY,
              backTo = fetch(R.string.NowPlaying)
            )
          )
        }
    }
  }

  private fun goToAlbumArtist(audioItem: QueueItem) {
    localAudioQueueModel.clearPrompt()
    scope.launch {
      audioMediaDao
        .getAlbumArtistId(audioItem.id)
        .onFailure { cause -> LOG.e(cause) { it("Error getting Album ArtistId %s", audioItem.id) } }
        .onSuccess { artistId ->
          backstack.goToScreen(
            ArtistSongsScreen(
              artistId = artistId,
              artistType = ArtistType.AlbumArtist,
              artistName = audioItem.albumArtist,
              artwork = Uri.EMPTY,
              backTo = fetch(R.string.NowPlaying)
            )
          )
        }
    }
  }

  override fun showCurrentItemDialog() {
    currentItem.let { item ->
      localAudioQueueModel.showPrompt(
        DialogPrompt(
          prompt = {
            CurrentItemDialog(
              audioItem = item,
              onDismiss = { localAudioQueueModel.clearPrompt() },
              showMediaInfo = ::displayMediaInfo,
              selectAlbumArt = ::selectAlbumArt,
              goToAlbum = ::goToAlbum,
              goToArtist = ::goToArtist,
              goToAlbumArtist = ::goToAlbumArtist
            )
          }
        )
      )
    }
  }
}

private fun LocalAudioQueueState.maybeNewQueue(state: NowPlayingState) =
  if (state.queue.differsFrom(queue, queueIndex)) queue.asNowPlayingList else state.queue

private fun List<QueueItem>.differsFrom(queue: List<AudioItem>, queueIndex: Int): Boolean = when {
  size != queue.size -> true
  else -> !sameAs(queue, queueIndex)
}

/**
 * Only checking 3 items at most - the current index, the previous, and the next (accounting for
 * queue boundaries). These are the only items we are concerned with in NowPlaying
 */
private fun List<QueueItem>.sameAs(queue: List<AudioItem>, queueIndex: Int): Boolean {
  debugRequire(size == queue.size) { "Queue sizes don't match" }
  val fromIndex = (queueIndex - 1).coerceAtLeast(0)
  val toIndex = (fromIndex + 2).coerceAtMost(size)
  return subList(fromIndex, toIndex)
    .zip(queue.subList(fromIndex, toIndex))
    .all { (queueItem, audioItem) -> queueItem.sameAs(audioItem) }
}

private fun QueueItem.sameAs(audioItem: AudioItem): Boolean = id == audioItem.id &&
  title == audioItem.title &&
  albumTitle == audioItem.albumTitle &&
  albumArtist == audioItem.albumArtist &&
  artist == audioItem.artist &&
  duration == audioItem.duration &&
  rating == audioItem.rating &&
  localAlbumArt == audioItem.localAlbumArt &&
  albumArt == audioItem.albumArt

private val List<AudioItem>.asNowPlayingList: List<QueueItem>
  get() = map { audioItem -> audioItem.asQueueItem }

private inline val AudioItem.asQueueItem: QueueItem
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
    albumId
  )

