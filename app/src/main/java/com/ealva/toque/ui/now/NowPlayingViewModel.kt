/*
 * Copyright 2021 eAlva.com
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
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.toDurationString
import com.ealva.toque.log._e
import com.ealva.toque.persist.MediaIdList
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
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.StreamVolume
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
  val presets: List<EqPreset>,
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
      presets = emptyList()
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
  fun play(selector: TransitionSelector)
  fun pause(selector: TransitionSelector)
  fun nextRepeatMode()
  fun toggleApplyEq()
  fun seekTo(position: Millis)
  fun beginUserSeek()
  fun endUserSeek(lastValue: Int)
  fun goToQueueIndexMaybePlay(index: Int)
  fun mediaIsLoaded(): Boolean

  //  fun paletteUpdated(it: NowPlayingPaletteData, id: Long)
  fun getItemFromId(id: Long): AudioItem
  fun deleteMedia(mediaId: Long)

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

  fun beginUserSeeking()
  fun endUserSeeking()

  //  fun setCurrentQueue(type: TheMediaController.QueueType)
  fun moveQueueItem(from: Int, to: Int)
  fun removeQueueItemAt(position: Int)
  fun moveQueueItemAfterCurrent(position: Int): Int

  /**
   * Play the list of media, returns true if media enqueued and played, false if the user should be
   * prompted re clear or play next
   */
  fun play(mediaIdList: MediaIdList): Boolean

  /**
   * Shuffle the list of media, returns true if media enqueued and played, false if the user should
   * be prompted re clear or play next
   */
  fun shuffle(mediaIdList: MediaIdList): Boolean

  fun playNext(
    mediaIdList: MediaIdList,
    clearUpNext: Boolean,
    shouldPlayNext: Boolean
  )

  fun addToUpNext(mediaIdList: MediaIdList)

  fun toggleShowTimeRemaining()

//  fun updatePlayStatusIfCurrent(mediaId: Long, playStatus: PlayStatus): Boolean

  companion object {
    const val INVALID_INDEX = -1

    operator fun invoke(
      serviceConnection: MediaPlayerServiceConnection,
      appPrefsSingleton: AppPrefsSingleton
    ): NowPlayingViewModel =
      NowPlayingViewModelImpl(serviceConnection, appPrefsSingleton)
  }
}

private val LOG by lazyLogger(NowPlayingViewModelImpl::class)

@OptIn(ExperimentalCoroutinesApi::class)
private class NowPlayingViewModelImpl(
  private val serviceConnection: MediaPlayerServiceConnection,
  private val appPrefsSingleton: AppPrefsSingleton
) : NowPlayingViewModel, ScopedServices.Activated, ScopedServices.Registered, Bundleable {
  private lateinit var scope: CoroutineScope
  private var mediaController: ToqueMediaController = NullMediaController
  private var appPrefs: AppPrefs? = null
  private var controllerJob: Job? = null
  private var currentQueueJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

  override fun onServiceRegistered() {
    LOG._e { it("onServiceRegistered") }
    scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch {
      val prefs = appPrefsSingleton.instance()
      appPrefs = prefs
      scope.launch {
        prefs.showTimeRemaining
          .asFlow()
          .collect { showTimeRemaining ->
            nowPlayingState.emit(nowPlayingState.value.copy(showTimeRemaining = showTimeRemaining))
          }
      }
    }
  }

  override fun onServiceUnregistered() {
    LOG._e { it("onServiceUnregistered") }
    appPrefs = null
    scope.cancel()
  }

  override fun toBundle(): StateBundle {
    LOG._e { it("toBundle") }
    return StateBundle().putString("testKey", "AString")
  }

  override fun fromBundle(bundle: StateBundle?) {
    val theBundle = bundle ?: StateBundle()
    LOG._e { it("fromBundle %s", theBundle.getString("testKey") ?: "null") }
  }

  override fun onServiceActive() {
    LOG._e { it("onServiceActive") }
    controllerJob = scope.launch {
      serviceConnection.mediaController
        .onEach { controller -> handleControllerChange(controller) }
        .onCompletion { handleControllerChange(NullMediaController) }
        .collect()
    }
  }

  override fun onServiceInactive() {
    LOG._e { it("onServiceInactive") }
    controllerJob?.cancel()
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    LOG._e { it("handleControllerChange") }
    mediaController = controller
    if (controller !== NullMediaController) {
      currentQueueJob = scope.launch {
        controller.currentQueue
          .onEach { queue -> handleQueueChange(queue) }
          .collect()
      }
    } else {
      currentQueueJob?.cancel()
      handleQueueChange(NullPlayableMediaQueue)
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    LOG._e { it("handleQueueChange") }
    when (queue.queueType) {
      QueueType.Audio -> queueActive(queue as LocalAudioQueue)
      else -> queueInactive()
    }
  }

  private fun queueActive(queue: LocalAudioQueue) {
    LOG._e { it("queueActive") }
    audioQueue = queue
    scope.launch {
      audioQueue.queueState
        .onEach { state -> handleServiceState(state) }
        .catch { cause -> LOG.e(cause) { it("") } }
        .onCompletion { LOG._e { it("LocalAudioQueue state flow completed") } }
        .collect()
    }
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
      extraMediaInfo = queueState.extraMediaInfo
    )
    nowPlayingState.emit(newState)
  }

  private fun queueInactive() {
    audioQueue = NullLocalAudioQueue
  }

  override fun nextShuffleMode() {
    TODO("Not yet implemented")
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
    LOG._e { it("previousList") }
    scope.launch { audioQueue.previousList() }
  }

  override fun togglePlayPause() {
    scope.launch { audioQueue.togglePlayPause() }
  }

  override fun play(selector: TransitionSelector) {
    TODO("Not yet implemented")
  }

  override fun play(mediaIdList: MediaIdList): Boolean {
    TODO("Not yet implemented")
  }

  override fun pause(selector: TransitionSelector) {
    TODO("Not yet implemented")
  }

  override fun nextRepeatMode() {
    TODO("Not yet implemented")
  }

  override fun toggleApplyEq() {
    TODO("Not yet implemented")
  }

  override fun seekTo(position: Millis) {
    scope.launch { audioQueue.seekTo(position) }
  }

  override fun beginUserSeek() {
    TODO("Not yet implemented")
  }

  override fun endUserSeek(lastValue: Int) {
    TODO("Not yet implemented")
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    scope.launch { audioQueue.goToIndexMaybePlay(index) }
  }

  override fun mediaIsLoaded(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getItemFromId(id: Long): AudioItem {
    TODO("Not yet implemented")
  }

  override fun deleteMedia(mediaId: Long) {
    TODO("Not yet implemented")
  }

  override fun scheduleSleepTimer(duration: Millis) {
    TODO("Not yet implemented")
  }

  override val remainingSleepTimer: Millis
    get() = TODO("Not yet implemented")

  override val streamVolume: StreamVolume
    get() = TODO()

  override fun beginUserSeeking() {
    TODO("Not yet implemented")
  }

  override fun endUserSeeking() {
    TODO("Not yet implemented")
  }

  override fun moveQueueItem(from: Int, to: Int) {
    TODO("Not yet implemented")
  }

  override fun removeQueueItemAt(position: Int) {
    TODO("Not yet implemented")
  }

  override fun moveQueueItemAfterCurrent(position: Int): Int {
    TODO("Not yet implemented")
  }

  override fun shuffle(mediaIdList: MediaIdList): Boolean {
    TODO("Not yet implemented")
  }

  override fun playNext(mediaIdList: MediaIdList, clearUpNext: Boolean, shouldPlayNext: Boolean) {
    TODO("Not yet implemented")
  }

  override fun addToUpNext(mediaIdList: MediaIdList) {
    TODO("Not yet implemented")
  }

  override fun toggleShowTimeRemaining() {
    appPrefs?.let { prefs ->
      scope.launch { prefs.showTimeRemaining.set(!prefs.showTimeRemaining()) }
    }
  }
}
