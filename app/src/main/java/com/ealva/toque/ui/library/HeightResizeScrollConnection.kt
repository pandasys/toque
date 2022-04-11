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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Recalculates the [heightSubtrahend] (amount to be subtracted from height) based on receiving
 * [onPreScroll] events and the [maxSubtrahend]. [heightSubtrahend] can range between
 * 0..[maxSubtrahend] and is emitted as a flow as a means to recompose.
 */
interface HeightResizeScrollConnection : NestedScrollConnection {
  val heightSubtrahend: StateFlow<Int>
  var maxSubtrahend: Int

  companion object {
    operator fun invoke(): HeightResizeScrollConnection = HeightResizeScrollConnectionImpl()
  }
}

private class HeightResizeScrollConnectionImpl : HeightResizeScrollConnection {
  override val heightSubtrahend = MutableStateFlow(0)
  override var maxSubtrahend = Int.MAX_VALUE

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    heightSubtrahend.value = (heightSubtrahend.value - available.y.roundToInt())
      .coerceIn(0..maxSubtrahend)
    return Offset.Zero
  }
}
