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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Contains an optional composable [prompt] which should be displayed to the user if present. The
 * current idea is that these are always dialogs. The result of one prompt may be another. One
 * example is adding items to a playlist - the user can select a playlist (first dialog) or create
 * a new playlist (second dialog).
 *
 * Current design involves a StateFlow of these which are collected as state and displayed to the
 * user if [prompt] is not null. Since lambdas capture arguments, there is currently no need for
 * these prompts to accept data or have callbacks.
 */
@Immutable
sealed interface DialogPrompt {
  val prompt: (@Composable () -> Unit)?

  @Immutable
  object None : DialogPrompt {
    override val prompt: (@Composable () -> Unit)? = null
  }

  @Immutable
  data class PromptData(override val prompt: (@Composable () -> Unit)) : DialogPrompt

  companion object {
    operator fun invoke(prompt: (@Composable () -> Unit)): DialogPrompt =
      PromptData(prompt)
  }
}
