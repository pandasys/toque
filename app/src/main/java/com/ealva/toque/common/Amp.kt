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

inline fun Int.toAmp(): Amp = Amp(this.toFloat())
inline fun Float.toAmp(): Amp = Amp(this)

inline operator fun ClosedFloatingPointRange<Float>.contains(amp: Amp): Boolean =
  contains(amp.value)

/** Represents Amplitude */
inline class Amp(val value: Float) : Comparable<Amp> {
  override fun compareTo(other: Amp): Int = value.compareTo(other.value)
  override fun toString(): String = value.toString()

  companion object {
    val ZERO = 0.toAmp()
    val TWELVE = 12.toAmp()
  }
}

inline operator fun Amp.compareTo(rhs: Float): Int = value.compareTo(rhs)

inline operator fun Amp.plus(rhs: Amp): Amp = (value + rhs.value).toAmp()
inline operator fun Amp.plus(rhs: Float): Amp = (value + rhs).toAmp()

// inline fun Amp.coerceIn(range: ClosedFloatingPointRange<Float>): Amp {
//   require(!range.isEmpty()) { "Range must not be empty" }
//   return value.coerceIn(range.start, range.endInclusive).toAmp()
// }

inline fun Amp.clampTo(range: ClosedFloatingPointRange<Float>): Amp {
  require(!range.isEmpty()) { "Range must not be empty" }
  return value.coerceIn(range.start, range.endInclusive).toAmp()
}
