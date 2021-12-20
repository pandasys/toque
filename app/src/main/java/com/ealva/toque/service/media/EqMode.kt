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

package com.ealva.toque.service.media

import android.os.Parcelable
import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import kotlinx.parcelize.Parcelize

@Parcelize
enum class EqMode(
  override val id: Int,
  @StringRes val titleRes: Int
) : HasConstId, Parcelable {
  Off(0, R.string.EqualizerOff) {
    override fun next() = On
  },
  On(1, R.string.EqualizerOn) {
    override fun next() = Off
  };

  abstract fun next(): EqMode

  fun isOff(): Boolean = this === Off
  fun isOn(): Boolean = this === On
}
