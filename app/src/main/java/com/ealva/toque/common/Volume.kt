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

typealias VolumeRange = ClosedRange<Volume>

/**
 * Represents a Volume value, which is typically an integer between 0 and 100. Some Android
 * streams have a different range, so no attempt is made to coerce in 0..100 here. However,
 * constants here, such as [MAX], [HALF], and [RANGE] refer to the Toque (libVLC) range of 0..100.
 */
@JvmInline
value class Volume(val value: Int) : Comparable<Volume> {
  override fun toString(): String = value.toString()

  override fun compareTo(other: Volume): Int = value.compareTo(other.value)

  operator fun minus(rhs: Volume): Volume = Volume((value - rhs.value))
  operator fun minus(rhs: Int): Volume = Volume(value - rhs)
  operator fun plus(rhs: Volume): Volume = Volume(value + rhs.value)
  operator fun plus(rhs: Int): Volume = Volume(value + rhs)
  operator fun div(rhs: Volume): Volume = Volume(value / rhs.value)
  operator fun div(rhs: Int): Volume = Volume(value / rhs)

  @Suppress("NOTHING_TO_INLINE")
  inline operator fun invoke(): Int = value

  companion object {
    // Commonly used Volumes
    val NONE = Volume(0)
    val ONE = Volume(1)
    val HALF = Volume(50)
    val MAX = Volume(100)
    val RANGE: VolumeRange = NONE..MAX
  }
}

fun abs(volume: Volume): Volume = Volume(kotlin.math.abs(volume.value))
