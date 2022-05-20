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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * This Clock allows us to accelerate time so the test runs faster. Pass in all the responses
 * during construction, ensuring the list is large enough for the class under test.
 */
class ClockStub(private val instantList: List<Instant>) : Clock {
  private val index = AtomicInteger(-1)
  override fun now(): Instant = instantList[index.incrementAndGet()]

  companion object {
    operator fun invoke(now: Instant, duration: Duration, samples: Int): ClockStub = ClockStub(
      buildList {
        add(now)
        var nextSample = now.plus(duration)
        repeat(samples - 1) {
          add(nextSample)
          nextSample = nextSample.plus(duration)
        }
      }
    )
  }
}
