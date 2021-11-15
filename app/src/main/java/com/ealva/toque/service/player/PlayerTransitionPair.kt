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

package com.ealva.toque.service.player

import com.ealva.toque.common.Millis
import com.ealva.toque.service.audio.PlayerTransition

/**
 * Contains [PlayerTransition]s to be used when moving from media that is playing and should stop to
 * another media that should begin playing.
 */
interface PlayerTransitionPair {
  /**
   * This is the [PlayerTransition] that will be used to end the currently playing media
   */
  val exitTransition: PlayerTransition

  /**
   * This is the [PlayerTransition] that will be used when the next media is started
   */
  val enterTransition: PlayerTransition
}

data class CrossFadeTransition(
  private val fadeLength: Millis,
  private val forceFadeInVolumeStartZero: Boolean,
  override val exitTransition: PlayerTransition = ShutdownFadeOutTransition(fadeLength),
  override val enterTransition: PlayerTransition =
    FadeInTransition(fadeLength, forceFadeInVolumeStartZero)
) : PlayerTransitionPair

data class DirectTransition(
  override val exitTransition: PlayerTransition = ShutdownImmediateTransition(),
  override val enterTransition: PlayerTransition = PlayImmediateTransition()
) : PlayerTransitionPair

object NoOpMediaTransition : PlayerTransitionPair {
  override val exitTransition: PlayerTransition = NoOpPlayerTransition
  override val enterTransition: PlayerTransition = NoOpPlayerTransition
  override fun toString(): String = "NoOpMediaTransition"
}

data class ShutdownCurrentEnterOtherTransition(
  private val enter: PlayerTransition,
  override val exitTransition: PlayerTransition = ShutdownImmediateTransition(),
  override val enterTransition: PlayerTransition = enter
) : PlayerTransitionPair
