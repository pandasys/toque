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

private const val START = .25f
private const val END = 4.0f
private val VALID_RANGE = START..END

@Parcelize
@JvmInline
value class PlaybackRate(val value: Float) : Comparable<PlaybackRate>, Parcelable {
  init {
    require(value in VALID_RANGE)
  }

  override fun compareTo(other: PlaybackRate): Int = value.compareTo(other.value)

  inline operator fun invoke(): Float = value

  companion object {
    operator fun invoke(rate: Double) = PlaybackRate(rate.toFloat())

    val RANGE = PlaybackRate(VALID_RANGE.start)..PlaybackRate(VALID_RANGE.endInclusive)
    val NORMAL = PlaybackRate(1.0)
  }
}
