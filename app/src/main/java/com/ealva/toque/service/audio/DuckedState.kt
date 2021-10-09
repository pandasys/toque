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

package com.ealva.toque.service.audio

import com.ealva.toque.prefs.DuckAction

/**
 * Because we want to share the concept of "ducked state" across player instances, and possibly
 * other objects, the ducked state will be maintained at a level in the containment hierarchy higher
 * than the player.
 */
interface DuckedState {
  /**
   * Current ducked state. Can be read by anyone but should only be set by the owner
   */
  var state: DuckAction

  /** Is the current action ducked */
  val ducked: Boolean
    get() = state === DuckAction.Duck

  /** Is the current action paused */
  val paused: Boolean
    get() = state === DuckAction.Pause

  companion object {
    operator fun invoke(): DuckedState = DuckedStateImpl()
  }
}

private class DuckedStateImpl : DuckedState {
  override var state: DuckAction = DuckAction.None
}
