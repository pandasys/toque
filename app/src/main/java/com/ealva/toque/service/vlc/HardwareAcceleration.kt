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

import android.content.res.Resources
import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasDescription

enum class HardwareAcceleration(
  override val id: Int,
  @StringRes private val stringRes: Int
) : HasConstId, HasDescription {
  DISABLED(1, R.string.Disabled),
  AUTOMATIC(2, R.string.Automatic),
  DECODING(3, R.string.DecodingAcceleration),
  FULL(4, R.string.FullAcceleration);

  override fun description(resources: Resources): String {
    return resources.getString(stringRes)
  }
}
