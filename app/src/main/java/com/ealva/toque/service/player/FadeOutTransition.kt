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
import com.ealva.toque.common.SuspendingThrottle
import com.ealva.toque.common.Volume
import com.ealva.toque.service.audio.PlayerTransition.Type
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val PERMITS_PER_SECOND = 10.0

private val ADJUST_FROM_END = Millis.TWO_HUNDRED
private val MIN_FADE_LENGTH = Millis.ONE_HUNDRED
private val MIN_VOLUME_STEP = Volume.ONE

/**
 * Base FadeOut transition
 */
abstract class FadeOutTransition(
  /** Requested length of fade in millis may be adjusted depending on remaining duration */
  protected val requestedFadeLength: Millis,
  /** The throttle only need be set for test */
  suspendingThrottle: SuspendingThrottle? = null,
  /** The dispatcher only need be changed for test */
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BasePlayerTransition(Type.Pause) {

  private val throttle = suspendingThrottle ?: SuspendingThrottle(PERMITS_PER_SECOND)

  override val isPlaying: Boolean = false
  override val isPaused: Boolean = true

  override suspend fun doExecute(player: TransitionPlayer) {
    val length: Millis = requestedFadeLength.coerceAtMost(player.remainingTime - ADJUST_FROM_END)

    if (length > MIN_FADE_LENGTH) {
      supervisorScope {
        launch(dispatcher) {
          val cancelVolume = player.volumeRange.start - 1
          maybeNotifyPaused(player)
          val steps: Millis = length / MIN_FADE_LENGTH

          val startVolume = player.volume
          val endVolume = player.volumeRange.start
          val volumeChange = ((endVolume - startVolume) / steps).coerceAtLeast(MIN_VOLUME_STEP)
          var newVolume = startVolume
          while (newVolume > cancelVolume) {
            newVolume = (newVolume - volumeChange).coerceAtLeast(Volume.NONE)
            player.volume = newVolume
            if (shouldContinueTransition(player)) {
              if (newVolume <= Volume.NONE) {
                finishTransition(player)
                newVolume = cancelVolume
              } else {
                throttle.acquire()
              }
            } else {
              if (isCancelled) {
                cancelTransition(player)
              }
              newVolume = cancelVolume
            }
          }
          if (!isFinished) finishTransition(player)
        }
      }
    } else {
      finishTransition(player)
    }
  }

  protected open fun maybeNotifyPaused(player: TransitionPlayer) {
    player.notifyPaused()
  }

  protected abstract fun cancelTransition(player: TransitionPlayer)

  protected abstract fun finishTransition(player: TransitionPlayer)
}
