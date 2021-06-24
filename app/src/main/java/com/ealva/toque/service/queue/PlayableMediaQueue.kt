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

import com.ealva.toque.common.Title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface PlayableMediaQueue<T : QueueMediaItem> {
  /**
   * The type of this media queue
   */
  val queueType: QueueType

  val isActive: StateFlow<Boolean>

  /**
   * Activate the queue, reestablishing index and position if [resume] is true, start playing if
   * [startPlayer] is true
   */
  suspend fun activate(
    resume: Boolean,
    startPlayer: Boolean,
    haveWritePermission: Boolean
  )

  fun deactivate()

  val queue: List<T>

  val currentItem: T

  val currentItemIndex: Int

  suspend fun getNextMediaTitle(): Title

  fun play(immediate: Boolean = false)

  fun pause(immediate: Boolean = false)

  fun togglePlayPause()

  fun next()

  fun previous()

  fun goToIndexMaybePlay(index: Int)

  val streamVolume: StreamVolume
}

object NullPlayableMediaQueue : PlayableMediaQueue<NullQueueMediaItem> {
  override val queueType: QueueType = QueueType.NullQueue
  override suspend fun activate(
    resume: Boolean,
    startPlayer: Boolean,
    haveWritePermission: Boolean
  ) = Unit

  override fun deactivate() = Unit
  override val isActive = MutableStateFlow(false)
  override val queue: List<NullQueueMediaItem> = emptyList()
  override val currentItem: NullQueueMediaItem = NullQueueMediaItem
  override val currentItemIndex: Int = -1
  override suspend fun getNextMediaTitle(): Title = NullQueueMediaItem.title
  override fun play(immediate: Boolean) = Unit
  override fun pause(immediate: Boolean) = Unit
  override fun togglePlayPause() = Unit
  override fun next() = Unit
  override fun previous() = Unit
  override fun goToIndexMaybePlay(index: Int) = Unit
  override val streamVolume: StreamVolume = NullStreamVolume
}
