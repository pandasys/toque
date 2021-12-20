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

package com.ealva.toque.service.audio

import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.StarRating
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.service.notify.ServiceNotification
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.ForceTransition
import com.ealva.toque.service.queue.NullStreamVolume
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.StreamVolume
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

object NullLocalAudioQueue : LocalAudioQueue {
  override val queueState = MutableStateFlow(LocalAudioQueueState.NONE)
  override val notification = emptyFlow<ServiceNotification>()
  override fun toggleEqMode() = Unit
  override fun setRating(rating: StarRating, allowFileUpdate: Boolean) = Unit
  override suspend fun addToUpNext(
    categoryMediaList: CategoryMediaList
  ): Result<QueueSize, QueueMessage> = Err(UnknownError)

  override suspend fun playNext(
    categoryMediaList: CategoryMediaList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transitionType: TransitionType
  ): Result<QueueSize, QueueMessage> = Err(UnknownError)


  override fun goToQueueItem(instanceId: InstanceId) = Unit
  override suspend fun prepareNext(categoryMediaList: CategoryMediaList) = Unit
  override fun nextRepeatMode() = Unit
  override fun nextShuffleMode() = Unit
  override fun setRepeatMode(mode: RepeatMode) = Unit
  override fun setShuffleMode(mode: ShuffleMode) = Unit
  override suspend fun removeFromQueue(index: Int, item: AudioItem) = Unit
  override fun moveQueueItem(from: Int, to: Int) = Unit
  override val isActive: StateFlow<Boolean> = MutableStateFlow(false)
  override suspend fun activate(resume: Boolean, playNow: PlayNow) = Unit
  override fun deactivate() = Unit
  override fun play(forceTransition: ForceTransition) = Unit
  override fun pause(forceTransition: ForceTransition) = Unit
  override fun stop() = Unit
  override fun togglePlayPause() = Unit
  override fun next() = Unit
  override fun previous() = Unit
  override fun nextList() = Unit
  override fun previousList() = Unit
  override fun seekTo(position: Millis) = Unit
  override fun fastForward() = Unit
  override fun rewind() = Unit
  override fun goToIndexMaybePlay(index: Int) = Unit
  override val streamVolume: StreamVolume = NullStreamVolume
}
