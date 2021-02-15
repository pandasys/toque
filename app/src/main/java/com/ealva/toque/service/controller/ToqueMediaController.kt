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

package com.ealva.toque.service.controller

import android.net.Uri
import androidx.lifecycle.LiveData
import com.ealva.toque.common.Millis
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.session.PlaybackActions

interface ToqueMediaController {
  val isActive: LiveData<Boolean>

  val mediaIsLoaded: Boolean

  fun setCurrentQueue(type: QueueType)

  /**
   * Start playing current media, if loaded, and apply the selected [transition]
   */
  fun play(transition: TransitionSelector)

  /**
   * Stop the current media item.
   */
  fun stop()

  /**
   * Pause current media item applying the selected transition
   */
  fun pause(transition: TransitionSelector)

  val isSeekable: Boolean

  fun seekTo(position: Millis)

  fun nextShuffleMode(): ShuffleMode

  fun nextRepeatMode(): RepeatMode

  val position: Millis

  val duration: Millis

  /**
   * If toggle state from play to pause
   */
  fun togglePlayPause()

  /**
   * Go to next media item. If streaming then there is no next media item.
   */
  fun next()

  /**
   * Go to previous media item. If streaming there is no previous media item.
   */
  fun previous()

  /** Go to the index of the up next queue and play if currently playing */
  fun goToQueueIndexMaybePlay(index: Int)
  fun loadUri(uri: Uri?)

  /** Current playback actions */
  val enabledActions: PlaybackActions
}
