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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LibraryScrollBar(
  listState: LazyListState,
  modifier: Modifier,
  content: @Composable () -> Unit
) {
  val config = LocalScreenConfig.current

  LazyColumnScrollbar(
    listState = listState,
    modifier = modifier,
    thumbMinHeight = if (config.inPortrait) 0.1F else 0.15F,
    content = content
  )
}
