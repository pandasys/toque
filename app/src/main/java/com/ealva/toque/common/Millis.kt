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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.common

import java.util.Date

typealias MillisRange = ClosedRange<Millis>

inline fun Long.toMillis(): Millis = Millis(this)
inline fun Int.toMillis(): Millis = Millis(toLong())

@JvmInline
value class Millis(val value: Long) : Comparable<Millis> {
  override fun toString(): String = value.toString()

  override operator fun compareTo(other: Millis): Int = value.compareTo(other.value)

  inline operator fun minus(rhs: Millis): Millis = (value - rhs.value).toMillis()
  inline operator fun minus(rhs: Long): Millis = (value - rhs).toMillis()

  inline operator fun div(other: Millis): Millis = (value / other.value).toMillis()

  inline operator fun plus(rhs: Millis): Millis = (value + rhs.value).toMillis()
  inline operator fun plus(rhs: Long): Millis = (value + rhs).toMillis()

  inline operator fun compareTo(rhs: Long): Int = value.compareTo(rhs)
  inline operator fun compareTo(rhs: Int): Int = value.compareTo(rhs)

  inline fun toDate(): Date = Date(value)

  companion object {
    // Some commonly used Millis
    val ZERO = Millis(0)
    val ONE_HUNDRED = Millis(100)
    val TWO_HUNDRED = Millis(200)
    val ONE_SECOND = Millis(1000)
    val THREE_SECONDS = Millis(3000)
    val ONE_MINUTE = Millis(60000)
  }
}
