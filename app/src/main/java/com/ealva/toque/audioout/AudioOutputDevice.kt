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

package com.ealva.toque.audioout

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.res.HasTitle

enum class AudioOutputDevice(
  override val id: Int,
  private val deviceId: String,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle {
  Mono(1, "mono", R.string.Mono),
  Stereo(2, "stereo", R.string.Stereo),
  HDMI(3, "hdmi", R.string.HDMI);

  override fun toString(): String = deviceId
}
