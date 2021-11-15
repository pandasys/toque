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
import com.ealva.toque.persist.reifyRequire
import com.ealva.toque.res.HasTitle

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toAudioOutputRoute(): AudioOutputRoute =
  AudioOutputRoute::class.reifyRequire(this.toInt())

inline val AudioOutputRoute.longId: Long
  get() = id.toLong()

enum class AudioOutputRoute(
  override val id: Int,
  @StringRes override val titleRes: Int
) : HasConstId, HasTitle {
  /** Audio is being output via the device's built-in speakers */
  Speaker(1, R.string.Speaker),

  /** Audio is being output via the device's headphone jack */
  HeadphoneJack(2, R.string.HeadphoneJack),

  /** Audio is being output via a Bluetooth connection */
  Bluetooth(3, R.string.Bluetooth);
}
