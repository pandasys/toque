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

import com.ealva.toque.common.Volume
import com.ealva.toque.common.asVolume
import com.ealva.toque.flow.CountDownFlow
import com.ealva.toque.service.audio.PlayerTransition.Type
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val ADJUST_FROM_END = 200.toDuration(DurationUnit.MILLISECONDS)
private val MIN_FADE_LENGTH = 100.toDuration(DurationUnit.MILLISECONDS)

/**
 * PlayerTransition that fades out from start volume to min volume over [requestedDuration]. We will
 * use a [CountDownFlow] over the fade length which will emit approximately every [MIN_FADE_LENGTH].
 * An emission contains the remaining duration of the fade and we will calculate the desired volume
 * from that remaining time.
 *
 * We calculate a factor which when multiplied by the remaining duration of fade gives the desired
 * volume. The [requestedDuration] may be adjusted down if the start volume is not max. If the
 * user changes media rapidly, or rapidly presses play/pause, a fade in may not have reached max
 * volume, so fading out can be shortened - no reason to set the same volume repeatedly.
 */
abstract class FadeOutTransition(
  protected val requestedDuration: Duration,
  dispatcher: CoroutineDispatcher
) : BasePlayerTransition(Type.Pause, dispatcher) {

  override val isPlaying: Boolean = false
  override val isPaused: Boolean = true

  override fun doExecute(player: TransitionPlayer) {
    val startVolume = player.playerVolume
    val maxVolume = player.volumeRange.endInclusive.coerceAtMost(startVolume)
    val minFadeStartVolumeAdjustment = requestedDuration * (startVolume.value / 100.0)
    val duration: Duration = requestedDuration
      .coerceAtMost(player.remainingTime - ADJUST_FROM_END)
      .coerceAtMost(minFadeStartVolumeAdjustment)

    if (duration > MIN_FADE_LENGTH) {
      val countDownFlow = CountDownFlow(duration, MIN_FADE_LENGTH)
      val multiplier = maxVolume.value.toDouble() / duration.inWholeMilliseconds

      countDownFlow
        .onStart { maybeNotifyPaused(player) }
        .onEach { remaining -> player.playerVolume = remaining.asVolume(multiplier) }
        .onCompletion { cause -> if (cause == null) finishTransition(player) }
        .launchIn(scope)
    } else {
      finishTransition(player)
    }
  }

  protected open fun maybeNotifyPaused(player: TransitionPlayer) = player.notifyPaused()
  protected abstract fun finishTransition(player: TransitionPlayer)
}

fun Duration.asVolume(multiplier: Double): Volume = (inWholeMilliseconds * multiplier).asVolume
