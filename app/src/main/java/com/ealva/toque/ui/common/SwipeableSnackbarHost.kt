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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.google.accompanist.insets.navigationBarsPadding

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeableSnackbarHost(hostState: SnackbarHostState) {
  if (hostState.currentSnackbarData == null) { return }
  var size by remember { mutableStateOf(Size.Zero) }
  val swipeableState = rememberSwipeableState(SwipeDirection.Initial)
  val width = remember(size) {
    if (size.width == 0f) {
      1f
    } else {
      size.width
    }
  }
  if (swipeableState.isAnimationRunning) {
    DisposableEffect(Unit) {
      onDispose {
        when (swipeableState.currentValue) {
          SwipeDirection.Right,
          SwipeDirection.Left -> {
            hostState.currentSnackbarData?.dismiss()
          }
          else -> {
            return@onDispose
          }
        }
      }
    }
  }
  val offset = with(LocalDensity.current) {
    swipeableState.offset.value.toDp()
  }
  SnackbarHost(
    hostState,
    snackbar = { snackbarData ->
      Snackbar(
        snackbarData,
        modifier = Modifier.offset(x = offset)
      )
    },
    modifier = Modifier
      .navigationBarsPadding(bottom = false)
      .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
      .swipeable(
        state = swipeableState,
        anchors = mapOf(
          -width to SwipeDirection.Left,
          0f to SwipeDirection.Initial,
          width to SwipeDirection.Right,
        ),
        thresholds = { _, _ -> FractionalThreshold(0.3f) },
        orientation = Orientation.Horizontal
      )
  )
}

private enum class SwipeDirection {
  Left,
  Initial,
  Right,
}

