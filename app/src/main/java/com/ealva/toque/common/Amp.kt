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

fun Int.toAmp(): Amp = Amp(this.toFloat())
fun Float.toAmp(): Amp = Amp(this)

operator fun ClosedFloatingPointRange<Float>.contains(amp: Amp): Boolean =
  contains(amp.value)

/** Represents Amplitude */
@JvmInline
value class Amp(val value: Float) : Comparable<Amp> {
  override fun compareTo(other: Amp): Int = value.compareTo(other.value)
  override fun toString(): String = value.toString()

  companion object {
    operator fun invoke(amp: Int) = Amp(amp.toFloat())

    val ZERO = 0.toAmp()
    val TWELVE = 12.toAmp()
  }
}

operator fun Amp.compareTo(rhs: Float): Int = value.compareTo(rhs)

operator fun Amp.plus(rhs: Amp): Amp = (value + rhs.value).toAmp()
operator fun Amp.plus(rhs: Float): Amp = (value + rhs).toAmp()
