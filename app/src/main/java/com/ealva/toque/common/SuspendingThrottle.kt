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

package com.ealva.toque.common

import engineering.clientside.throttle.Throttle
import kotlinx.coroutines.delay

interface SuspendingThrottle {
  /**
   * Acquires a single permit from this throttle, suspending until the request can be granted.
   * Returns the amount of time delayed in seconds, if any.
   */
  suspend fun acquire(): Double

  companion object {
    operator fun invoke(permitsPerSecond: Double): SuspendingThrottle =
      SuspendingThrottleImpl(permitsPerSecond)
  }
}

private const val NANOSECONDS_PER_MILLISECOND = 1000000
private const val ONE_SECOND_NANOS = 1000000000.0

private class SuspendingThrottleImpl(permitsPerSecond: Double) : SuspendingThrottle {
  private val throttle = Throttle.create(permitsPerSecond)

  override suspend fun acquire(): Double {
    val waitDuration: Long = throttle.acquireDelayDuration(1)
    delay(waitDuration / NANOSECONDS_PER_MILLISECOND)
    return waitDuration / ONE_SECOND_NANOS
  }
}

/**
 * This throttle does not throttle, just immediately returns 0.0
 */
object NullSuspendingThrottle : SuspendingThrottle {
  override suspend fun acquire(): Double = 0.0
}
