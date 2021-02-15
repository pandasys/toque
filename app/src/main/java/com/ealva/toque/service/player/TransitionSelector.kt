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

package com.ealva.toque.service.player

/**
 * Determines which transition is applied during a play or pause.
 */
enum class TransitionSelector {
  /**
   * Apply an immediate transition regardless of any user settings.
   */
  Immediate,

  /**
   * Use the current transition as selected by the user. This might be a fade or could be
   * immediate. See the AppPreferences for relevant settings.
   */
  Current;

  inline fun selectPlay(current: () -> PlayerTransition): PlayerTransition = when (this) {
    Immediate -> PlayImmediateTransition()
    Current -> current()
  }
  fun selectPause(current: () -> PlayerTransition): PlayerTransition = when (this) {
    Immediate -> PauseImmediateTransition()
    Current -> current()
  }
}
