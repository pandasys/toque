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


fun <E> MutableList<E>.swap(index1: Int, index2: Int) = apply {
  val tmp = this[index1]
  this[index1] = this[index2]
  this[index2] = tmp
}

/**
 * Returns a sublist of max size 15, which is a window around [currentItemIndex]
 */
fun <T> List<T>.windowOf15(currentItemIndex: Int): List<T> {
  if (isEmpty()) return this
  debug { require(currentItemIndex in indices) }
  val halfWindowSize = 7
  val windowSize = 2 * halfWindowSize + 1
  val position = currentItemIndex + 1
  var fromIndex = 0
  var toIndex = size.coerceAtMost(windowSize)
  if (position > halfWindowSize) {
    toIndex = (position + halfWindowSize).coerceAtMost(size)
    fromIndex = (toIndex - windowSize).coerceAtLeast(0)
  }
  return subList(fromIndex, toIndex)
}
