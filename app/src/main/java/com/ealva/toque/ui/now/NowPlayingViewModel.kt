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
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.toDurationString
import com.ealva.toque.log._e
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.StreamVolume
import com.ealva.toque.ui.audio.LocalAudioQueueModel
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

@Immutable
data class NowPlayingState(
  val queue: List<AudioItem>,
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
      localAudioQueueModel: LocalAudioQueueModel,
      appPrefsSingleton: AppPrefsSingleton
    ): NowPlayingViewModel =
      NowPlayingViewModelImpl(localAudioQueueModel, appPrefsSingleton)
  }
}

private val LOG by lazyLogger(NowPlayingViewModelImpl::class)

/**
 * This view model does not save it's state as that information is already saved in the
 * MediaPlayerService. When we connect we will receive the state information.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class NowPlayingViewModelImpl(
  private val localAudioQueueModel: LocalAudioQueueModel,
  private val appPrefsSingleton: AppPrefsSingleton
) : NowPlayingViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

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
      .onCompletion { LOG._e { it("LocalAudioQueue state flow completed") } }
      .launchIn(scope)
  }

  private fun queueInactive() {
    queueStateJob?.cancel()
    queueStateJob = null
    audioQueue = NullLocalAudioQueue
  }

  private fun handleServiceState(queueState: LocalAudioQueueState) {
    nowPlayingState.update {
      it.copy(
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
    }
  }

  override fun nextShuffleMode() {
    audioQueue.nextShuffleMode()
  }

  override fun nextMedia() {
    audioQueue.next()
  }

  override fun previousMedia() {
    audioQueue.previous()
  }

  override fun nextList() {
    audioQueue.nextList()
  }

  override fun previousList() {
    audioQueue.previousList()
  }

  override fun togglePlayPause() {
    audioQueue.togglePlayPause()
  }

  override fun nextRepeatMode() {
    audioQueue.nextRepeatMode()
  }

  override fun toggleEqMode() {
    audioQueue.toggleEqMode()
  }

  override fun seekTo(position: Millis) {
    audioQueue.seekTo(position)
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    audioQueue.goToIndexMaybePlay(index)
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
}
