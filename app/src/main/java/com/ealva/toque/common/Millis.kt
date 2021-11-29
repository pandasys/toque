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

@file:Suppress("NOTHING_TO_INLINE")

package com.ealva.toque.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

typealias MillisRange = ClosedRange<Millis>

private const val MILLIS_PER_SECOND = 1000.0F

@Parcelize
@JvmInline
value class Millis(val value: Long) : Comparable<Millis>, Parcelable {
  inline operator fun invoke(): Long = value

  override fun toString(): String = value.toString()

  fun toFloatSeconds(): Float = value / MILLIS_PER_SECOND

  override operator fun compareTo(other: Millis): Int = value.compareTo(other.value)

  inline operator fun minus(rhs: Millis): Millis = Millis(value - rhs.value)
  inline operator fun minus(rhs: Long): Millis = Millis(value - rhs)

  inline operator fun times(rhs: Millis): Millis = Millis(value * rhs.value)
  inline operator fun times(rhs: Long): Millis = Millis(value * rhs)
  inline operator fun div(rhs: Millis): Millis = Millis(value / rhs.value)

  inline operator fun plus(rhs: Millis): Millis = Millis(value + rhs.value)
  inline operator fun plus(rhs: Long): Millis = Millis(value + rhs)

  inline operator fun compareTo(rhs: Long): Int = value.compareTo(rhs)
  inline operator fun compareTo(rhs: Int): Int = value.compareTo(rhs)

  fun asPercentageOf(total: Millis): Double = value.toDouble() / total.value

  inline fun toDate(): Date = Date(value)

  companion object {
    operator fun invoke(value: Int): Millis = Millis(value.toLong())
    operator fun invoke(value: Float): Millis = Millis(value.toLong())

    inline fun currentTime(): Millis = Millis(System.currentTimeMillis())

    val ONE_SECOND = Millis(1000)
    val TWO_SECONDS = Millis(2000)
    val THREE_SECONDS = Millis(3000)
    val FIVE_SECONDS = Millis(5000)
    val TEN_SECONDS = Millis(10000)
    val ONE_MINUTE = Millis(60000)
    val FOUR_MINUTES = Millis(240000)
  }
}

fun Long.asMillis(): Millis = Millis(this)
