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

package com.ealva.toque.service.queue

import com.ealva.toque.common.Millis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface PlayableMediaQueue<T : Any> {
  /**
   * The type of this media queue
   */
  val queueType: QueueType
  val isActive: StateFlow<Boolean>

  /**
   * Activate the queue, reestablishing index and position if [resume] is true, start playing if
   * [playNow] is true
   */
  suspend fun activate(
    resume: Boolean,
    playNow: PlayNow
  )

  fun deactivate()
  fun play(immediateTransition: Boolean = false)
  suspend fun pause(immediateTransition: Boolean = false)
  suspend fun stop()
  suspend fun togglePlayPause()
  suspend fun next()
  suspend fun previous()
  suspend fun nextList()
  suspend fun previousList()
  suspend fun seekTo(position: Millis)
  suspend fun fastForward()
  suspend fun rewind()
  suspend fun goToIndexMaybePlay(index: Int)
  suspend fun duck()
  suspend fun endDuck()
  val streamVolume: StreamVolume
}

object NullPlayableMediaQueue : PlayableMediaQueue<NullQueueMediaItem> {
  override val queueType: QueueType = QueueType.NullQueue
  override suspend fun activate(
    resume: Boolean,
    playNow: PlayNow
  ) = Unit

  override fun deactivate() = Unit
  override val isActive = MutableStateFlow(false)
  override fun play(immediateTransition: Boolean) = Unit
  override suspend fun pause(immediateTransition: Boolean) = Unit
  override suspend fun stop() = Unit
  override suspend fun togglePlayPause() = Unit
  override suspend fun next() = Unit
  override suspend fun previous() = Unit
  override suspend fun nextList() = Unit
  override suspend fun previousList() = Unit
  override suspend fun goToIndexMaybePlay(index: Int) = Unit
  override val streamVolume: StreamVolume = NullStreamVolume
  override suspend fun fastForward() = Unit
  override suspend fun rewind() = Unit
  override suspend fun seekTo(position: Millis) = Unit
  override suspend fun duck() = Unit
  override suspend fun endDuck() = Unit
}
