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

inline fun Long.toMillis(): Millis = Millis(this)
inline fun Int.toMillis(): Millis = Millis(toLong())

inline class Millis(val value: Long) {
  override fun toString(): String = value.toString()
}

inline operator fun Millis.compareTo(rhs: Millis): Int = value.compareTo(rhs.value)
inline operator fun Millis.compareTo(rhs: Long): Int = value.compareTo(rhs)
inline operator fun Millis.compareTo(rhs: Int): Int = value.compareTo(rhs)

inline fun Millis.coerceIn(range: LongRange): Millis = value.coerceIn(range).toMillis()

inline fun Millis.toDate(): Date = Date(value)
inline fun Millis.toFloat(): Float = value.toFloat()
