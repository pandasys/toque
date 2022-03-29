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

import android.content.Intent
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface PlayableMediaQueue<T : Any> {
  /**
   * The type of this media queue
   */
  val queueType: QueueType
  val isActive: StateFlow<Boolean>
  val streamVolume: StreamVolume

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

  /** Go to the next logical [RepeatMode]  */
  fun nextRepeatMode()

  /** Go to the next logical [ShuffleMode] */
  fun nextShuffleMode()

  fun handleScreenAction(action: ScreenAction, keyguardLocked: Boolean)
}

enum class ScreenAction(val intentAction: String) {
  On(Intent.ACTION_SCREEN_ON),
  Off(Intent.ACTION_SCREEN_OFF);

  companion object {
    fun screenAction(intent: Intent?): ScreenAction? = when (intent?.action) {
      On.intentAction -> On
      Off.intentAction -> Off
      else -> null
    }
  }
}

object NullPlayableMediaQueue : PlayableMediaQueue<NullQueueMediaItem> {
  override val queueType: QueueType = QueueType.NullQueue
  override val isActive = MutableStateFlow(false)
  override val streamVolume: StreamVolume = NullStreamVolume

  override suspend fun activate(
    resume: Boolean,
    playNow: PlayNow
  ) = Unit

  override fun deactivate() = Unit
  override fun play(mayFade: MayFade) = Unit
  override fun pause(mayFade: MayFade) = Unit
  override fun stop() = Unit
  override fun togglePlayPause() = Unit
  override fun next() = Unit
  override fun previous() = Unit
  override fun nextList() = Unit
  override fun previousList() = Unit
  override fun fastForward() = Unit
  override fun rewind() = Unit
  override fun seekTo(position: Millis) = Unit
  override fun goToIndexMaybePlay(index: Int) = Unit
  override fun handleScreenAction(action: ScreenAction, keyguardLocked: Boolean) = Unit
  override fun nextRepeatMode() = Unit
  override fun nextShuffleMode() = Unit
}
