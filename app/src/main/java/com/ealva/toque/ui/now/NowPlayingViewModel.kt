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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.audio.QueueAudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.toDurationString
import com.ealva.toque.log._i
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.StreamVolume
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class NowPlayingState(
  val queue: List<QueueAudioItem>,
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
  val currentItem: AudioItem
    get() = if (queueIndex in queue.indices) queue[queueIndex] else NullPlayableAudioItem

  fun getDurationDisplay(): String =
    (if (showTimeRemaining) position - duration else duration).toDurationString()

  fun getPositionDisplay(): String = position.toDurationString()

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

interface NowPlayingViewModel {
  val nowPlayingState: StateFlow<NowPlayingState>
//  suspend fun getItemArt(itemId: Long, caller: String): Pair<Uri?, AlbumSongArtInfo?>

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

//  fun updatePlayStatusIfCurrent(mediaId: Long, playStatus: PlayStatus): Boolean

  companion object {
    operator fun invoke(
      serviceConnection: MediaPlayerServiceConnection,
      appPrefsSingleton: AppPrefsSingleton
    ): NowPlayingViewModel =
      NowPlayingViewModelImpl(serviceConnection, appPrefsSingleton)
  }
}

private val LOG by lazyLogger(NowPlayingViewModelImpl::class)

/**
 * This view model does not save it's state as that information is already saved in the
 * MediaPlayerService. When we connect we will receive the state information.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class NowPlayingViewModelImpl(
  private val serviceConnection: MediaPlayerServiceConnection,
  private val appPrefsSingleton: AppPrefsSingleton
) : NowPlayingViewModel, ScopedServices.Activated, ScopedServices.Registered {
  private lateinit var scope: CoroutineScope
  private var mediaController: ToqueMediaController = NullMediaController
  private var appPrefs: AppPrefs? = null
  private var controllerJob: Job? = null
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

  override fun onServiceRegistered() {
    scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch {
      val prefs = appPrefsSingleton.instance()
      appPrefs = prefs
      prefs.showTimeRemaining
        .asFlow()
        .onEach { showTimeRemaining ->
          nowPlayingState.emit(nowPlayingState.value.copy(showTimeRemaining = showTimeRemaining))
        }
        .launchIn(scope)
    }
  }

  override fun onServiceUnregistered() {
    appPrefs = null
    scope.cancel()
  }

  override fun onServiceActive() {
    controllerJob = serviceConnection.mediaController
      .onEach { controller -> handleControllerChange(controller) }
      .onCompletion { handleControllerChange(NullMediaController) }
      .launchIn(scope)
  }

  override fun onServiceInactive() {
    controllerJob?.cancel()
    controllerJob = null
    handleControllerChange(NullMediaController)
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    mediaController = controller
    if (controller !== NullMediaController) {
      currentQueueJob = controller.currentQueue
        .onEach { queue -> handleQueueChange(queue) }
        .launchIn(scope)
    } else {
      currentQueueJob?.cancel()
      currentQueueJob = null
      handleQueueChange(NullPlayableMediaQueue)
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    when (queue.queueType) {
      QueueType.Audio -> queueActive(queue as LocalAudioQueue)
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

  private suspend fun handleServiceState(queueState: LocalAudioQueueState) {
    val newState = nowPlayingState.value.copy(
      queue = queueState.queue,
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
    nowPlayingState.emit(newState)
  }

  override fun nextShuffleMode() {
    audioQueue.nextShuffleMode()
  }

  override fun nextMedia() {
    scope.launch { audioQueue.next() }
  }

  override fun previousMedia() {
    scope.launch { audioQueue.previous() }
  }

  override fun nextList() {
    scope.launch { audioQueue.nextList() }
  }

  override fun previousList() {
    scope.launch { audioQueue.previousList() }
  }

  override fun togglePlayPause() {
    scope.launch { audioQueue.togglePlayPause() }
  }

  override fun nextRepeatMode() {
    audioQueue.nextRepeatMode()
  }

  override fun toggleEqMode() {
    audioQueue.toggleEqMode()
  }

  override fun seekTo(position: Millis) {
    scope.launch { audioQueue.seekTo(position) }
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    scope.launch { audioQueue.goToIndexMaybePlay(index) }
  }

  override fun scheduleSleepTimer(duration: Millis) {
    TODO("Not yet implemented")
  }

  override val remainingSleepTimer: Millis
    get() = TODO("Not yet implemented")

  override val streamVolume: StreamVolume
    get() = TODO()

  override fun toggleShowTimeRemaining() {
    appPrefs?.let { prefs ->
      scope.launch { prefs.showTimeRemaining.set(!prefs.showTimeRemaining()) }
    }
  }
}

//private fun List<QueueAudioItem>.toInfoQueue(): List<QueueAudioItem> = this
//map {
//QueueAudioInfo(
//  id = it.id,
//  instanceId = it.instanceId,
//  title = it.title,
//  albumTitle = it.albumTitle,
//  albumArtist = it.albumArtist,
//  artist = it.artist,
//  duration = it.duration,
//  trackNumber = it.trackNumber,
//  localAlbumArt = it.localAlbumArt,
//  albumArt = it.albumArt,
//  rating = it.rating,
//  location = it.location,
//  fileUri = it.fileUri
//)
//}
