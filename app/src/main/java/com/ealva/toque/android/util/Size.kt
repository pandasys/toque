/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.android.util

import android.util.Size


private fun Size.withinBoundary(boundary: Size): Boolean =
  width <= boundary.width && height <= boundary.height

/**
 * If width or height exceed the boundary, return a scaled size to be within the boundary while
 * maintaining the original aspect ratio
 */
fun Size.coerceIn(boundary: Size): Size = if (withinBoundary(boundary)) this else {
  val originalWidth = width
  val originalHeight = height
  val boundWidth = boundary.width
  val boundHeight = boundary.height
  var newWidth = originalWidth
  var newHeight = originalHeight

  if (originalWidth > boundWidth) {
    newWidth = boundWidth
    //scale height to maintain aspect ratio
    newHeight = newWidth * originalHeight / originalWidth
  }

  if (newHeight > boundHeight) {
    newHeight = boundHeight
    //scale width to maintain aspect ratio
    newWidth = newHeight * originalWidth / originalHeight
  }
  Size(newWidth, newHeight)
}
