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

import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.flow.CountDownFlow
import com.ealva.toque.service.audio.PlayerTransition.Type
import com.ealva.toque.service.player.FadeInTransition.Companion.MIN_FADE_LENGTH
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("unused")
private val LOG by lazyLogger(FadeInTransition::class)

/**
 * PlayerTransition that fades in from start volume to max volume over [requestedDuration]. We will
 * use a [CountDownFlow] over the fade length which will emit approximately every [MIN_FADE_LENGTH].
 * An emission contains the remaining duration of the fade and we will calculate the desired volume
 * from that remaining time.
 *
 * We calculate a factor which when multiplied by the remaining duration of fade, and subtracted
 * from max volume, gives the desired volume. The [requestedDuration] may be adjusted down if the
 * start volume is not 0. If the user changes media rapidly, or rapidly presses play/pause, a fade
 * out may not have reached min volume, so fading in can be shortened - no reason to set the same
 * volume repeatedly.
 */
class FadeInTransition(
  /** Requested fade duration, may be adjusted depending on remaining duration */
  private val requestedDuration: Duration,
  private val clock: Clock = Clock.System,
  dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BasePlayerTransition(Type.Play, dispatcher) {

  override val isPlaying: Boolean = true
  override val isPaused: Boolean = false

  override fun doExecute(player: TransitionPlayer) {
    val startVolume = player.playerVolume
    val maxVolume = player.volumeRange.endInclusive
    val minFadeStartVolumeAdjustment = (requestedDuration * (maxVolume - startVolume).value / 100.0)
    val duration: Duration = requestedDuration
      .coerceAtMost(player.remainingTime - ADJUST_FROM_END)
      .coerceAtMost(minFadeStartVolumeAdjustment)

    if (duration > MIN_FADE_LENGTH) {
      val countDownFlow = CountDownFlow(total = duration, interval = MIN_FADE_LENGTH, clock = clock)
      val multiplier = (maxVolume - startVolume).value.toDouble() / duration.inWholeMilliseconds

      countDownFlow
        .onStart { startAndNotify(player) }
        .onEach { remaining -> player.playerVolume = maxVolume - remaining.asVolume(multiplier) }
        .onCompletion { cause -> if (cause == null) setComplete() }
        .launchIn(scope)
    } else {
      player.playerVolume = player.volumeRange.endInclusive
      setComplete()
    }
  }

  private fun startAndNotify(player: TransitionPlayer) {
    if (!player.isPlaying) player.play()
    player.notifyPlaying()
  }

  override fun toString(): String = buildString {
    append("FadeInTransition(requestedFadeLength=")
    append(requestedDuration)
    append(')')
  }

  companion object {
    val ADJUST_FROM_END = 200.toDuration(DurationUnit.MILLISECONDS)
    val MIN_FADE_LENGTH = 100.toDuration(DurationUnit.MILLISECONDS)
  }
}
