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

enum class PlayUpNextAction(
  override val id: Int,
  override val titleRes: Int
) : HasConstId, HasTitle {
  ClearUpNext(1, R.string.ClearUpNext),
  PlayNext(2, R.string.PlayNext),
  Prompt(3, R.string.Prompt);
}
