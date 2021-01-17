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

/**
 * Contains [PlayerTransition]s to be used when moving from media that is playing and should stop to
 * another media that should begin playing.
 */
interface MediaExitEnterTransitionPair {
  /**
   * This is the [PlayerTransition] that will be used to end the currently playing media
   */
  val exitTransition: PlayerTransition

  /**
   * This is the [PlayerTransition] that will be used when the next media is started
   */
  val enterTransition: PlayerTransition
}

class CrossFadeTransition(
  private val fadeLength: Millis,
  private val forceFadeInVolumeStartZero: Boolean
) : MediaExitEnterTransitionPair {
  override val exitTransition: PlayerTransition
    get() = ShutdownFadeOutTransition(fadeLength)
  override val enterTransition: PlayerTransition
    get() = FadeInTransition(fadeLength, forceFadeInVolumeStartZero)
}

class DirectTransition : MediaExitEnterTransitionPair {
  override val exitTransition: PlayerTransition
    get() = ShutdownImmediateTransition()
  override val enterTransition: PlayerTransition
    get() = PlayImmediateTransition()
}

object NoOpMediaTransition : MediaExitEnterTransitionPair {
  override val exitTransition: PlayerTransition = NoOpPlayerTransition
  override val enterTransition: PlayerTransition = NoOpPlayerTransition
}

class ShutdownCurrentEnterOtherTransition(
  private val enter: PlayerTransition
) : MediaExitEnterTransitionPair {
  override val exitTransition: PlayerTransition
    get() = ShutdownImmediateTransition()
  override val enterTransition: PlayerTransition
    get() = enter
}
