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

interface TransitionPlayer {
  val isPlaying: Boolean

  val isPaused: Boolean

  val isShutdown: Boolean

  var volume: Volume

  val volumeRange: VolumeRange

  val remainingTime: Millis

  fun notifyPaused()

  fun notifyPlaying()

  fun pause()

  fun play()

  fun shutdown()

  fun shouldContinue(): Boolean
}

object NullTransitionPlayer : TransitionPlayer {
  override val isPlaying: Boolean
    get() = false

  override val isPaused: Boolean
    get() = false

  override val isShutdown: Boolean
    get() = false

  override var volume: Volume
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
  override fun shutdown() = Unit
  override fun shouldContinue() = false
}
