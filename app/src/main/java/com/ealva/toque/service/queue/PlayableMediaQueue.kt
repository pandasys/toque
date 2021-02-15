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

interface PlayableMediaQueue<T : QueueMediaItem> {
  /**
   * What type of item is in the queue
   */
  val queueType: QueueType

  /**
   * Activate the queue, reestablishing index and position if [resume] is true, start playing if
   * [startPlayer] is true
   */
  fun activate(
    resume: Boolean,
    startPlayer: Boolean,
    haveWritePermission: Boolean
  )

  fun deactivate()

  val isActive: Boolean

  val upNextQueue: List<T>

  val currentItem: T

  val currentItemIndex: Int

  suspend fun getNextMediaTitle(): String

  fun play(immediate: Boolean = false)

  fun pause(immediate: Boolean = false)

  fun togglePlayPause()

  fun next()

  fun previous()

  fun goToIndexMaybePlay(index: Int)

  val streamVolume: StreamVolume
}
