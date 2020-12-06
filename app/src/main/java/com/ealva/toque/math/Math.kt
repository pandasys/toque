/*
 * Copyright 2020 eAlva.com
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

@file:Suppress("NOTHING_TO_INLINE", "MagicNumber")

package com.ealva.toque.math

import com.ealva.toque.common.debug
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

inline fun Double.clamp(min: Double, max: Double): Double = max(min, min(this, max))

inline fun Float.clamp(min: Float, max: Float): Float = max(min, min(this, max))

inline fun Int.clamp(min: Int, max: Int) = min(max(this, min), max)

inline fun Int.clamp(range: IntRange) = min(max(this, range.first), range.last)

@Suppress("unused")
fun Long.clamp(min: Long, max: Long) = min(max(this, min), max)

inline fun Int.hash(): Int {
  return this
}

inline fun Long.hash(): Int {
  return (xor(ushr(32))).toInt()
}

inline fun Double.hash(): Int {
  val bits = java.lang.Double.doubleToLongBits(this)
  return (bits xor bits.ushr(32)).toInt()
}

inline fun Float.hash(): Int {
  return java.lang.Float.floatToIntBits(this)
}

/**
 * Returns true if this and [rhs] are within [epsilon] of each other. Floating point numbers should
 * not be directly compared and instead should be considered equal if within some arbitrarily small
 * number ([epsilon]), which you select for a given situation. [epsilon] must be positive
 *
 * 1.009.isEqualTo(1.001, epsilon = 0.01) returns true
 */
inline fun Double.isEqualTo(rhs: Double, epsilon: Double): Boolean {
  debug { require(epsilon > 0.0) }
  return abs(this - rhs) < epsilon
}

@Suppress("unused")
inline fun Double.isNotEqualTo(rhs: Double, epsilon: Double): Boolean {
  return !isEqualTo(rhs, epsilon)
}

inline fun Double.isZero(epsilon: Double): Boolean {
  debug { require(epsilon > 0.0) }
  return isEqualTo(0.0, epsilon)
}

@Suppress("unused")
inline fun Double.isNotZero(epsilon: Double): Boolean {
  return !isZero(epsilon)
}

/**
 * This must be in the range 0.0..1.0 and is multiplied by 100.0 and converted to Int
 */
inline fun Double.percentageToInt(): Int {
  debug { require(this in 0.0..1.0) }
  return (this * 100.0).clamp(0.0, 100.0).toInt()
}

inline fun Float.isEqualTo(rhs: Float, epsilon: Float): Boolean {
  debug { require(epsilon > 0.0) }
  return abs(this - rhs) < epsilon
}

inline fun Float.isNotEqualTo(rhs: Float, epsilon: Float): Boolean {
  return !isEqualTo(rhs, epsilon)
}

inline fun Float.isZero(epsilon: Float): Boolean {
  debug { require(epsilon > 0.0) }
  return isEqualTo(0.0F, epsilon)
}

@Suppress("unused")
inline fun Float.isNotZero(epsilon: Float): Boolean {
  return !isZero(epsilon)
}
