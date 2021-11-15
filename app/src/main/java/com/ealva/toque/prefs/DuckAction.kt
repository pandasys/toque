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

package com.ealva.toque.prefs

import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle

/**
 * This is the action the user prefers when the audio manager asks us to duck and it is also the
 * state of the player while ducked.
 *
 * The player is typically created in the [None] state, though could be in a ducked state, and
 * then transitions to other states based on the the AudioFocusManager, combined with user
 * preference, says is appropriate.
 */
enum class DuckAction(
  override val id: Int,
  override val titleRes: Int
) : HasConstId, HasTitle {
  Duck(1, R.string.Duck),
  Pause(2, R.string.Pause),
  None(3, R.string.DoNothing);
}
