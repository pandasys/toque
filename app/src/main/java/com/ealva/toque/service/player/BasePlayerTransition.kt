/*
 * Copyright 2022 Eric A. Snell
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

import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.service.audio.PlayerTransition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

@Suppress("unused")
private val LOG by lazyLogger(BasePlayerTransition::class)

abstract class BasePlayerTransition(
  override val type: PlayerTransition.Type,
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PlayerTransition {
  protected val scope = CoroutineScope(Job() + dispatcher)
  private var player: TransitionPlayer = NullTransitionPlayer
  private var complete = false

  override val isFinished: Boolean get() = isCancelled || complete
  override val isActive: Boolean get() = !isFinished
  override val isPlaying: Boolean get() = player.isPlaying
  override val isPaused: Boolean get() = player.isPaused
  override var isCancelled: Boolean = false

  override fun accept(nextTransition: PlayerTransition): Boolean =
    type.canTransitionTo(nextTransition.type)

  override fun setPlayer(transitionPlayer: TransitionPlayer) {
    player = transitionPlayer
  }

  override fun execute() {
    if (!isFinished) {
      doExecute(player)
    }
  }

  protected abstract fun doExecute(player: TransitionPlayer)

  override fun setCancelled() {
    if (!isFinished) {
      scope.cancel()
      isCancelled = true
      cancelTransition(player)
    }
  }

  protected open fun cancelTransition(player: TransitionPlayer) = Unit

  protected fun setComplete() {
    complete = true
  }
}

/**
 * Does nothing (obviously)
 */
object NoOpPlayerTransition : BasePlayerTransition(PlayerTransition.Type.NoOp) {
  override fun doExecute(player: TransitionPlayer) = setComplete()
}
