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

package com.ealva.toque.service.queue

import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange

interface StreamVolume {
  /** Is the volume fixed (not adjustable) */
  val volumeIsFixed: Boolean

  /** Min to max volume range for this stream */
  val volumeRange: VolumeRange

  /**
   * The current stream volume. Setting a value outside [volumeRange] clamps to that range, so a
   * resulting get may differ from a previous set if it was outside the range.
   */
  var streamVolume: Volume
}

object NullStreamVolume : StreamVolume {
  override val volumeIsFixed: Boolean = true
  override val volumeRange: VolumeRange = Volume.NONE..Volume.NONE
  override var streamVolume: Volume
    get() = Volume.NONE
    set(@Suppress("UNUSED_PARAMETER") value) {}
}
