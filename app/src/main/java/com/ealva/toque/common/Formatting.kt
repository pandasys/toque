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

package com.ealva.toque.common

import ealvatag.utils.TimeUnits
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.math.abs

private val BUILDER = StringBuilder(16)

fun Millis.toDurationString(): String = BUILDER.run {
  debug { require(isUiThread) }   // don't share BUILDER across threads
  val negative = value < 0
  var totalSeconds = TimeUnits.convert(abs(value), MILLISECONDS, SECONDS, true)

  val seconds = totalSeconds % 60
  totalSeconds /= 60
  val minutes = totalSeconds % 60
  totalSeconds /= 60
  val hours = totalSeconds

  setLength(0)
  if (negative) {
    append("-")
  }
  if (totalSeconds > 0) {
    append(hours)
    append(":")
    if (minutes < 10) {
      append("0")
    }
    append(minutes)
  } else {
    append(minutes)
  }

  append(":")
  if (seconds < 10) {
    append("0")
  }
  append(seconds)
}.toString()

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
fun Millis.toDateTime(): String {
  val instant = Instant.ofEpochMilli(value)
  val date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
  return dateTimeFormatter.format(date)
}
