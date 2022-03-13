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
  suspend fun activate(resume: Boolean, playNow: PlayNow)
  fun deactivate()

  fun play(mayFade: MayFade)
  fun pause(mayFade: MayFade)
  fun stop()
  fun togglePlayPause()
  fun next()
  fun previous()
  fun nextList()
  fun previousList()
  fun seekTo(position: Millis)
  fun fastForward()
  fun rewind()
  fun goToIndexMaybePlay(index: Int)
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
  override fun play(mayFade: MayFade) = Unit
  override fun pause(mayFade: MayFade) = Unit
  override fun stop() = Unit
  override fun togglePlayPause() = Unit
  override fun next() = Unit
  override fun previous() = Unit
  override fun nextList() = Unit
  override fun previousList() = Unit
  override val streamVolume: StreamVolume = NullStreamVolume
  override fun fastForward() = Unit
  override fun rewind() = Unit
  override fun seekTo(position: Millis) = Unit
  override fun goToIndexMaybePlay(index: Int) = Unit
}
