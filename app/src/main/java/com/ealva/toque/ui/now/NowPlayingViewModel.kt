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

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toTitle
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.AudioQueueItem
import com.ealva.toque.service.queue.NullAudioQueueItem
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.service.queue.StreamVolume
import com.ealva.toque.service.vlc.VlcEqPreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface NowPlayingViewModel {
  val currentIndex: StateFlow<Int>
  val currentItem: StateFlow<AudioQueueItem>
  val positionDuration: StateFlow<Pair<Millis, Millis>>
  val repeat: StateFlow<RepeatMode>
  val shuffle: StateFlow<ShuffleMode>
  val eqMode: StateFlow<EqMode>
  val playing: StateFlow<Boolean>

  //  val presets: StateFlow<List<Equalizer>>
  val currentPreset: StateFlow<EqPreset>
  val nextTitle: StateFlow<Title>
//  val itemUpdated: StateFlow<QueueItemUpdatedEvent>
//  val paletteUpdated: StateFlow<PaletteUpdate>

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
  fun getItemFromId(id: Long): AudioQueueItem
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
  }
}

inline val NowPlayingViewModel.isPlaying: Boolean
  get() = playing.value == true

// inline val NowPlayingViewModel.playStatus: PlayStatusUpdate
//  get() {
//    val (pos, dur) = positionDuration.value ?: Pair(0L, 0L)
//    return PlayStatusUpdate(pos, dur, isPlaying)
//  }

fun FragmentActivity.getNowPlayingViewModel(
  context: Context,
  addNewMedia: Boolean = false
): NowPlayingViewModel {
  @Suppress("EXPERIMENTAL_API_USAGE")
  return ViewModelProvider(
    this,
    NowPlayingViewModelFactory(context, addNewMedia)
  )[NowPlayingViewModelImpl::class.java]
}

private class NowPlayingViewModelFactory(
  private val context: Context,
  private val addNewMedia: Boolean
) : ViewModelProvider.Factory {
  @Suppress("EXPERIMENTAL_API_USAGE")
  override fun <T : ViewModel?> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(NowPlayingViewModelImpl::class.java))
    @Suppress("UNCHECKED_CAST")
    return NowPlayingViewModelImpl(context, addNewMedia) as T
  }
}

@Suppress("EXPERIMENTAL_API_USAGE")
fun Fragment.getNowPlayingViewModel(): NowPlayingViewModel {
  return ViewModelProvider(requireActivity())[NowPlayingViewModelImpl::class.java]
}

@OptIn(ExperimentalCoroutinesApi::class)
private val LOG by lazyLogger(NowPlayingViewModelImpl::class)
private const val ADD_NEW_MEDIA_COUNT = 10

@ExperimentalCoroutinesApi
private class NowPlayingViewModelImpl(
  private val context: Context,
  private var addNewMedia: Boolean
) : ViewModel(), NowPlayingViewModel {
  override val currentIndex: MutableStateFlow<Int> = MutableStateFlow(-1)
  override val currentItem: StateFlow<AudioQueueItem> = MutableStateFlow(NullAudioQueueItem)
  override val positionDuration: StateFlow<Pair<Millis, Millis>> =
    MutableStateFlow(Pair(Millis.ZERO, Millis.ZERO))
  override val repeat: StateFlow<RepeatMode> = MutableStateFlow(RepeatMode.None)
  override val shuffle: StateFlow<ShuffleMode> = MutableStateFlow(ShuffleMode.None)
  override val eqMode: StateFlow<EqMode> = MutableStateFlow(EqMode.Off)
  override val playing: StateFlow<Boolean> = MutableStateFlow(false)
  override val currentPreset: StateFlow<EqPreset> = MutableStateFlow(VlcEqPreset.NONE)
  override val nextTitle: StateFlow<Title> = MutableStateFlow("".toTitle())

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

  override fun getItemFromId(id: Long): AudioQueueItem {
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
