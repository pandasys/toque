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

package com.ealva.toque.flow

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

/**
 * Original from:
 * https://programmerr47.medium.com/count-down-timer-with-kotlin-coroutines-flow-a59c36167247
 */
@OptIn(FlowPreview::class)
class CountDownFlow(
  private val total: Duration,
  private val interval: Duration
) : AbstractFlow<Duration>() {
  private val clock = Clock.System

  init {
    require(interval.isPositive())
  }

  override suspend fun collectSafely(collector: FlowCollector<Duration>) {
    val deadline = clock.now() + total

    var remaining = total
    while (remaining > Duration.ZERO) {
      tick(collector, remaining)
      remaining = deadline - clock.now()
    }

    collector.emit(Duration.ZERO)
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun tick(collector: FlowCollector<Duration>, remaining: Duration) {
    val tickDuration: Duration = measureTime { collector.emit(remaining) }
    val remainingLessTick = remaining - tickDuration

    delay(
      if (remainingLessTick < interval) {
        remainingLessTick.coerceAtLeast(Duration.ZERO)
      } else {
        interval - tickDuration % interval
      }
    )
  }
}

operator fun Duration.rem(other: Duration): Duration =
  (inWholeNanoseconds % other.inWholeNanoseconds).nanoseconds
