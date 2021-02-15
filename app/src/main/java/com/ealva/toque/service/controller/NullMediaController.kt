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
import androidx.lifecycle.MutableLiveData
import com.ealva.toque.common.Millis
import com.ealva.toque.service.player.TransitionSelector
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.service.session.PlaybackActions

object NullMediaController : ToqueMediaController {
  override val isActive: LiveData<Boolean> = MutableLiveData(false)
  override val mediaIsLoaded: Boolean = false
  override fun setCurrentQueue(type: QueueType) = Unit
  override fun play(transition: TransitionSelector) = Unit
  override fun stop() = Unit
  override fun pause(transition: TransitionSelector) = Unit
  override val isSeekable: Boolean = false
  override fun seekTo(position: Millis) = Unit
  override fun nextShuffleMode(): ShuffleMode = ShuffleMode.None
  override fun nextRepeatMode(): RepeatMode = RepeatMode.None
  override val position: Millis = Millis.ZERO
  override val duration: Millis = Millis.ZERO
  override fun togglePlayPause() = Unit
  override fun next() = Unit
  override fun previous() = Unit
  override fun goToQueueIndexMaybePlay(index: Int) = Unit
  override fun loadUri(uri: Uri?) = Unit
  override val enabledActions: PlaybackActions = PlaybackActions()
}
