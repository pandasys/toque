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

package com.ealva.toque.service.player

import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange

/**
 * TransitionPlayer is the interface PlayerTransition implementations see. The TransitionPlayer
 * implementation is generally closer to the AvPlayer's underlying media player and will emit events
 * at times an AvPlayer would not. For example, if the user pauses and there is a fade out
 * transition, the transition will emit a Paused event while the player is still actually playing
 * and the volume is fading. Visually the user sees the pause button take effect as expected but the
 * audio is still fading, albeit very briefly. Also, during a cross fade, we don't want the user
 * to see the play/pause button react, so the transitions control if the overall state is
 * considered playing or paused.
 */
interface TransitionPlayer {
  val isPlaying: Boolean

  val isPaused: Boolean

  val playerIsShutdown: Boolean

  var playerVolume: Volume

  val volumeRange: VolumeRange

  val remainingTime: Millis

  fun notifyPaused()

  fun notifyPlaying()

  fun pause()

  fun play()

  fun shutdownPlayer()

  fun shouldContinue(): Boolean
}

object NullTransitionPlayer : TransitionPlayer {
  override val isPlaying: Boolean
    get() = false

  override val isPaused: Boolean
    get() = false

  override val playerIsShutdown: Boolean
    get() = false

  override var playerVolume: Volume
    get() = Volume.NONE
    set(@Suppress("UNUSED_PARAMETER") volume) {
    }
  override val volumeRange: VolumeRange
    get() = AvPlayer.DEFAULT_VOLUME_RANGE

  override fun notifyPaused() = Unit
  override fun notifyPlaying() = Unit
  override val remainingTime: Millis = Millis(0)
  override fun pause() = Unit
  override fun play() = Unit
  override fun shutdownPlayer() = Unit
  override fun shouldContinue() = false
}
