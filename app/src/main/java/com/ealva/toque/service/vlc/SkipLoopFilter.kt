/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.service.vlc

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle

enum class SkipLoopFilter(
  override val id: Int,
  private val value: Int,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle {
  Auto(1, -1, R.string.Auto),
  None(2, 0, R.string.None),
  NonRef(3, 1, R.string.NonRef),
  Bidir(4, 2, R.string.Bidir),
  NonKey(5, 3, R.string.NonKey),
  All(6, 4, R.string.All);

  override fun toString(): String = value.toString()

  companion object {
    val DEFAULT = Auto
  }
}
