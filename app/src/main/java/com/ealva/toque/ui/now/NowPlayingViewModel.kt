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

import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayState
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.StreamVolume
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class NowPlayingState(
  internal val queue: List<AudioItem>,
  val queueIndex: Int,
  val position: Millis,
  val duration: Millis,
  val playingState: PlayState,
  val repeatMode: RepeatMode,
  val shuffleMode: ShuffleMode,
  val eqMode: EqMode,
  val presets: List<EqPreset>,
  val currentPreset: EqPreset,
  val extraMediaInfo: String
) {
  val currentItem: AudioItem
    get() = if (queueIndex in queue.indices) queue[queueIndex] else NullPlayableAudioItem

  companion object {
    val NONE = NowPlayingState(
      queue = emptyList(),
      queueIndex = -1,
      position = Millis.ZERO,
      duration = Millis.ZERO,
      playingState = PlayState.Stopped,
      repeatMode = RepeatMode.None,
      shuffleMode = ShuffleMode.None,
      eqMode = EqMode.Off,
      presets = emptyList(),
      currentPreset = EqPreset.NONE,
      extraMediaInfo = ""
    )
  }
}

interface NowPlayingViewModel {
  val nowPlayingState: StateFlow<NowPlayingState>
//  suspend fun getItemArt(itemId: Long, caller: String): Pair<Uri?, AlbumSongArtInfo?>

  fun nextShuffleMode()
  fun nextMedia()
  fun previousMedia()
  fun togglePlayPause()
  fun play(selector: TransitionSelector)
  fun pause(selector: TransitionSelector)
  fun nextRepeatMode()
  fun toggleApplyEq()
  fun seekTo(value: Long)
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
   * @param duration duration of the sleep timer, or [Millis.ZERO] to cancel an already scheduled
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

// NOTE: Implement Bundleable to save and restore state

@OptIn(ExperimentalCoroutinesApi::class)
private class NowPlayingViewModelImpl(
  private val serviceConnection: MediaPlayerServiceConnection,
  private val appPrefsSingleton: AppPrefsSingleton
) : NowPlayingViewModel, ScopedServices.Registered {
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var mediaController: ToqueMediaController = NullMediaController
  private lateinit var appPrefs: AppPrefs
  private var connectedToService = false

  override val nowPlayingState = MutableStateFlow(NowPlayingState.NONE)

  override fun onServiceRegistered() {
    scope.launch {
      appPrefs = appPrefsSingleton.instance()
      serviceConnection.mediaController.value.currentQueue
      serviceConnection.mediaController.collect { controller ->
        mediaController = controller
        connectedToService = controller !== NullMediaController
        if (connectedToService) {
          mediaController = controller
        }
      }
    }
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun nextShuffleMode() {
    TODO("Not yet implemented")
  }

  override fun nextMedia() {
    TODO("Not yet implemented")
  }

  override fun previousMedia() {
    TODO("Not yet implemented")
  }

  override fun togglePlayPause() {
    TODO("Not yet implemented")
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

  override fun seekTo(value: Long) {
    TODO("Not yet implemented")
  }

  override fun beginUserSeek() {
    TODO("Not yet implemented")
  }

  override fun endUserSeek(lastValue: Int) {
    TODO("Not yet implemented")
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    TODO("Not yet implemented")
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
}
