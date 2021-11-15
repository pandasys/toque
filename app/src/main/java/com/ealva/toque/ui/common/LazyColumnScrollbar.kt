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

package com.ealva.toque.ui.common

/*
Original copyright. Has been modified.

https://github.com/nanihadesuka/LazyColumnScrollbar

Copyright (c) 2021 nani

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Scrollbar for LazyColumn
 *
 * * [rightSide] true -> right,  false -> left
 * * [thickness] Thickness of the scrollbar thumb
 * * [horizontalPadding]   Padding of the scrollbar
 * * [thumbMinHeight] Thumb minimum height proportional to total scrollbar's height
 * (eg: 0.1 -> 10% of total)
 */
@Composable
fun LazyColumnScrollbar(
  listState: LazyListState,
  modifier: Modifier = Modifier,
  rightSide: Boolean = true,
  thickness: Dp = 6.dp,
  horizontalPadding: Dp = 12.dp,
  thumbMinHeight: Float = 0.1f,
  thumbShape: Shape = CircleShape,
  colors: LazyColumnScrollbarColors = LazyColumnScrollbarDefaults.colors(),
  content: @Composable () -> Unit
) {
  Box {
    content()
    LazyColumnScrollbar(
      listState = listState,
      modifier = modifier,
      rightSide = rightSide,
      thickness = thickness,
      horizontalPadding = horizontalPadding,
      thumbMinHeight = thumbMinHeight,
      thumbShape = thumbShape,
      colors = colors
    )
  }
}

/**
 * Scrollbar for LazyColumn
 *
 * * [rightSide] true -> right,  false -> left
 * * [thickness] Thickness of the scrollbar thumb
 * * [horizontalPadding]   Padding of the scrollbar
 * * [thumbMinHeight] Thumb minimum height proportional to total scrollbar's height
 * (eg: 0.1 -> 10% of total)
 */
@Composable
private fun LazyColumnScrollbar(
  listState: LazyListState,
  modifier: Modifier,
  rightSide: Boolean,
  thickness: Dp,
  horizontalPadding: Dp,
  thumbMinHeight: Float,
  thumbShape: Shape,
  colors: LazyColumnScrollbarColors
) {
  val coroutineScope = rememberCoroutineScope()

  var isSelected by remember { mutableStateOf(false) }

  var dragOffset by remember { mutableStateOf(0f) }

  fun normalizedThumbSize(): Float = listState.layoutInfo.let { info ->
    if (info.totalItemsCount == 0) return@let 0f
    val firstPartial = info.visibleItemsInfo.first().run { -offset.toFloat() / size.toFloat() }
    val lastPartial = info.visibleItemsInfo.last()
      .run { 1f - (info.viewportEndOffset - offset).toFloat() / size.toFloat() }
    val realVisibleSize = info.visibleItemsInfo.size.toFloat() - firstPartial - lastPartial
    realVisibleSize / info.totalItemsCount.toFloat()
  }.coerceAtLeast(thumbMinHeight)

  fun normalizedOffsetPosition(): Float = listState.layoutInfo.let { info ->
    if (info.totalItemsCount == 0 || info.visibleItemsInfo.isEmpty()) 0f
    else info.visibleItemsInfo.first().run {
      index.toFloat() - offset.toFloat() / size.toFloat()
    } / info.totalItemsCount.toFloat()
  }

  fun setScrollOffset(newOffset: Float) {
    dragOffset = newOffset.coerceIn(0f, 1f)

    val exactIndex: Float = listState.layoutInfo.totalItemsCount.toFloat() * dragOffset
    val index: Int = floor(exactIndex).toInt()
    val remainder: Float = exactIndex - floor(exactIndex)

    coroutineScope.launch {
      listState.scrollToItem(index = index, scrollOffset = 0)
      val offset =
        listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.let { it.toFloat() * remainder }
          ?.toInt() ?: 0
      listState.scrollToItem(index = index, scrollOffset = offset)
    }
  }

  val isInAction = listState.isScrollInProgress || isSelected

  val alpha by animateFloatAsState(
    targetValue = if (isInAction) 1f else 0f,
    animationSpec = tween(
      durationMillis = if (isInAction) 75 else 500,
      delayMillis = if (isInAction) 0 else 500
    )
  )

  val displacement by animateFloatAsState(
    targetValue = if (isInAction) 0f else 14f,
    animationSpec = tween(
      durationMillis = if (isInAction) 75 else 500,
      delayMillis = if (isInAction) 0 else 500
    )
  )

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .then(modifier)
  ) {

    val maxHeight = constraints.maxHeight * (1.0F - normalizedThumbSize())

    val dragState = rememberDraggableState { delta ->
      setScrollOffset(dragOffset + delta / maxHeight)
    }

    BoxWithConstraints(
      Modifier
        .align(if (rightSide) Alignment.TopEnd else Alignment.TopStart)
        .alpha(alpha)
        .fillMaxHeight()
        .draggable(
          state = dragState,
          orientation = Orientation.Vertical,
          startDragImmediately = true,
          onDragStarted = { offset ->
            val newOffset = offset.y / maxHeight
            val currentOffset = normalizedOffsetPosition()

            if (currentOffset < newOffset && newOffset < currentOffset + normalizedThumbSize())
              dragOffset = currentOffset
            else
              setScrollOffset(newOffset)

            isSelected = true
          },
          onDragStopped = {
            isSelected = false
          }
        )
        .absoluteOffset(x = if (rightSide) displacement.dp else -displacement.dp)
    ) {
      Box(
        Modifier
          .align(Alignment.TopEnd)
          .graphicsLayer {
            translationY = maxHeight * normalizedOffsetPosition()
          }
          .padding(horizontal = horizontalPadding)
          .width(thickness)
          .clip(thumbShape)
          .background(colors.getThumbColor(isSelected))
          .fillMaxHeight(normalizedThumbSize())
      )
    }
  }
}

interface LazyColumnScrollbarColors {
  fun getThumbColor(isSelected: Boolean): Color
}

object LazyColumnScrollbarDefaults {
  @Composable
  fun colors(
    thumbColor: Color = MaterialTheme.colors.secondaryVariant,
    unselectedAlpha: Float = 0.54F,
    selectedAlpha: Float = 1F
  ): LazyColumnScrollbarColors = DefaultLazyColumnScrollbarColors(
    selected = thumbColor.copy(alpha = selectedAlpha),
    unselected = thumbColor.copy(alpha = unselectedAlpha)
  )
}

@Immutable
private class DefaultLazyColumnScrollbarColors(
  private val selected: Color,
  private val unselected: Color
) : LazyColumnScrollbarColors {
  override fun getThumbColor(isSelected: Boolean): Color =
    if (isSelected) selected else unselected

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DefaultLazyColumnScrollbarColors) return false

    if (selected != other.selected) return false
    if (unselected != other.unselected) return false

    return true
  }

  override fun hashCode(): Int {
    var result = selected.hashCode()
    result = 31 * result + unselected.hashCode()
    return result
  }
}
