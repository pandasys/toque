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

package com.ealva.toque.service.audio

import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.player.NoOpMediaTransition
import com.ealva.toque.service.player.PlayerTransitionPair
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.NullStreamVolume
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.StreamVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NullLocalAudioQueue : LocalAudioQueue {
  override val queueState: StateFlow<LocalAudioQueueState> =
    MutableStateFlow(LocalAudioQueueState.NONE)
  override val seeking: Boolean = false
  override val isSeekable: Boolean = false
  override val manualTransition: PlayerTransitionPair = NoOpMediaTransition
  override val autoAdvanceTransition: PlayerTransitionPair = NoOpMediaTransition
  override suspend fun setRepeatMode(mode: RepeatMode) = Unit
  override suspend fun setShuffleMode(mode: ShuffleMode) = Unit
  override suspend fun setRating(rating: StarRating, allowFileUpdate: Boolean) = Unit
  override suspend fun addToUpNext(audioIdList: AudioIdList) = Unit
  override suspend fun playNext(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transition: PlayerTransitionPair
  ) = Unit

  override suspend fun goToQueueItem(instanceId: Long) = Unit
  override suspend fun prepareNext(audioIdList: AudioIdList) = Unit
  override val isActive: StateFlow<Boolean> = MutableStateFlow(false)
  override suspend fun activate(resume: Boolean, playNow: PlayNow) = Unit
  override fun deactivate() = Unit
  override fun play(immediateTransition: Boolean) = Unit
  override suspend fun pause(immediateTransition: Boolean) = Unit
  override suspend fun stop() = Unit
  override suspend fun togglePlayPause() = Unit
  override suspend fun next() = Unit
  override suspend fun previous() = Unit
  override suspend fun nextList() = Unit
  override suspend fun previousList() = Unit
  override suspend fun seekTo(position: Millis) = Unit
  override suspend fun fastForward() = Unit
  override suspend fun rewind() = Unit
  override suspend fun goToIndexMaybePlay(index: Int) = Unit
  override suspend fun duck() = Unit
  override suspend fun endDuck() = Unit
  override val streamVolume: StreamVolume = NullStreamVolume
}
