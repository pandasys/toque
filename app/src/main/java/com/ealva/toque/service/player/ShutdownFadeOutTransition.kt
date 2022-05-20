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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * A fade out transition that does a shutdown on completion
 */
class ShutdownFadeOutTransition(
  fadeLength: Duration,
  clock: Clock = Clock.System,
  /** The dispatcher only need be changed for test */
  dispatcher: CoroutineDispatcher = Dispatchers.Default
) : FadeOutTransition(requestedDuration = fadeLength, clock = clock, dispatcher = dispatcher) {
  /**
   * Going to a shutdown state during a fade out, which means this is being used to
   * cross-fade, so we won't notify paused since we are already fading in another player. We
   * can add a boolean parameter later if needed.
   */
  override fun maybeNotifyPaused(player: TransitionPlayer) = Unit

  override fun finishTransition(player: TransitionPlayer) {
    player.shutdownPlayer()
    setComplete()
  }

  override fun cancelTransition(player: TransitionPlayer) = player.shutdownPlayer()

  override fun toString(): String = "ShutdownFadeOutTransition($requestedDuration)"
}
