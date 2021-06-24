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

typealias VolumeRange = ClosedRange<Volume>

inline fun Int.toVolume(): Volume = Volume(this)

@JvmInline
value class Volume(val value: Int) : Comparable<Volume> {
  override fun toString(): String = value.toString()

  override fun compareTo(other: Volume): Int = value.compareTo(other.value)
  inline operator fun minus(other: Volume): Volume = (value - other.value).toVolume()
  inline operator fun plus(other: Volume): Volume = (value + other.value).toVolume()
  inline operator fun div(other: Volume): Volume = (value / other.value).toVolume()
  inline operator fun div(other: Int): Volume = (value / other).toVolume()

  inline operator fun plus(rhs: Int): Volume = (value + rhs).toVolume()

  companion object {
    // Commonly used Volumes
    val ZERO = Volume(0)
    val ONE = Volume(1)
    val FIFTY = Volume(50)
    val ONE_HUNDRED = Volume(100)
  }
}
