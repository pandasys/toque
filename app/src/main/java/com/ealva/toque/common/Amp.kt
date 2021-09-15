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

operator fun ClosedFloatingPointRange<Float>.contains(amp: Amp): Boolean =
  contains(amp())

/** Represents Amplitude */
@JvmInline
value class Amp(val value: Float) : Comparable<Amp> {
  override fun compareTo(other: Amp): Int = value.compareTo(other.value)
  operator fun compareTo(other: Float): Int = value.compareTo(other)

  operator fun plus(rhs: Amp): Amp = Amp(value + rhs.value)
  operator fun plus(rhs: Float): Amp = Amp(value + rhs)

  inline operator fun invoke(): Float = value

  override fun toString(): String = value.toString()

  companion object {
    inline operator fun invoke(value: Int) = Amp(value.toFloat())

    val NONE = Amp(0F)
    val DEFAULT_PREAMP = Amp(12F)
    val MIN = Amp(-20F)
    val MAX = Amp(20F)
    val RANGE = MIN..MAX
  }
}
