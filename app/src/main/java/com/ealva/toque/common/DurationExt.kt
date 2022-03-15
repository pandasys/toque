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

package com.ealva.toque.common

import kotlin.math.absoluteValue
import kotlin.time.Duration

private inline fun <T> Duration.toAbsComponents(
  action: (hours: Long, minutes: Int, seconds: Int) -> T
): T = toComponents { hours, minutes, seconds, _ ->
  action(hours.absoluteValue, minutes.absoluteValue, seconds.absoluteValue)
}

private val BUILDER = StringBuilder(16)
val Duration.asDurationString: String
  get() = toAbsComponents { hours, minutes, seconds ->
    debug { require(isUiThread) }   // don't share BUILDER across threads
    BUILDER.run {
      setLength(0)

      if (isNegative()) append("-")

      if (hours > 0) {
        append(hours)
        append(":")
        if (minutes < 10) append("0")
        append(minutes)
      } else append(minutes)

      append(":")

      if (seconds < 10) append("0")
      append(seconds)
    }.toString()
  }
