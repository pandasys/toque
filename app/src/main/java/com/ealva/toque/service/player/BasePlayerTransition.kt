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
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.audio.PlayerTransition.Type

@Suppress("NOTHING_TO_INLINE")
inline operator fun Volume.div(rhs: Millis): Volume = Volume(value / rhs().toInt())

/**
 * Concrete transitions subclass this and only need implement [doExecute] and anything
 * supporting that.
 */
abstract class BasePlayerTransition(override val type: Type) : PlayerTransition {
  final override var isCancelled: Boolean = false
    private set

  private var complete: Boolean = false
  private var player: TransitionPlayer = NullTransitionPlayer

  override val isFinished: Boolean
    get() = isCancelled || complete

  override val isPaused: Boolean
    get() = player.isPaused

  override val isPlaying: Boolean
    get() = player.isPlaying

  override fun accept(nextTransition: PlayerTransition): Boolean {
    return type.canTransitionTo(nextTransition.type)
  }

  override fun setPlayer(transitionPlayer: TransitionPlayer) {
    player = transitionPlayer
  }

  final override suspend fun execute() {
    if (!isFinished) {
      doExecute(player)
    }
  }

  /** Subclasses must override this to perform the actual transition */
  protected abstract suspend fun doExecute(player: TransitionPlayer)

  override fun setCancelled() {
    isCancelled = true
  }

  fun setComplete() {
    complete = true
  }

  fun shouldContinueTransition(player: TransitionPlayer): Boolean {
    return !isFinished && player.shouldContinue()
  }
}

/**
 * Does nothing (obviously)
 */
object NoOpPlayerTransition : BasePlayerTransition(Type.NoOp) {
  override suspend fun doExecute(player: TransitionPlayer) {
    setComplete()
  }
}
