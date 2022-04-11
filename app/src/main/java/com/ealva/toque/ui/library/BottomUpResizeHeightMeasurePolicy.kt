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

package com.ealva.toque.ui.library

import androidx.compose.ui.layout.MeasurePolicy

@Suppress("FunctionName")
fun BottomUpResizeHeightMeasurePolicy(
  heightSubtrahend: Int,
  scrollConnection: HeightResizeScrollConnection,
  minimumHeight: Int = 0
): MeasurePolicy = MeasurePolicy { measurables, constraints ->
  val itemConstraints = constraints.copy(minWidth = 0)
  val placeables = measurables.mapIndexed { index, measurable ->
    when (index) {
      measurables.indices.last -> measurable.measure(constraints)
      else -> measurable.measure(itemConstraints)
    }
  }

  val width = placeables.maxOf { placeable -> placeable.width }
  val maxHeight = placeables.sumOf { placeable -> placeable.height }
    .coerceAtLeast(minimumHeight)
  val minHeight = placeables.last().height
  if (heightSubtrahend == 0) scrollConnection.maxSubtrahend = maxHeight - minHeight
  val height = (maxHeight - heightSubtrahend)
    .coerceAtLeast(minHeight)

  layout(width, height) {
    var y = height
    placeables.asReversed().forEachIndexed { index, placeable ->
      y -= placeable.height
      if (index == placeables.indices.last) {
        y = y.coerceAtMost(0)
      }
      placeable.place(x = 0, y = y)
    }
  }
}
