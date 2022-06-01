/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.common

import com.ealva.toque.R

/**
 * Represents the midpoint of an Equalizer band.
 */
@JvmInline
value class Frequency(val value: Float) {
  /**
   * Convert [value] to a Hertz string representation (only Hz and kHz supported)
   */
  val displayString: String
    get() = value.toHertzString()
}

/** Since [Float.toInt] rounds down, add 0.5 before conversion. */
private fun Float.toHertzString() = if (this < 999.5f) asHertz else asKiloHertz

private inline val Float.asHertz get() = (this + 0.5f).toInt().toString() + " Hz"

private inline val Float.asKiloHertz get() = (this / 1000.0f + 0.5f).toInt().toString() + " kHz"

/**
 * Represents an Equalizer band and it's frequency
 */
data class EqBand(val index: Int, val frequency: Frequency) {
  val title: String
    get() = if (frequency.value <= 0F) fetch(R.string.Preamp) else frequency.displayString

  companion object {
    val PreAmp = EqBand(-1, Frequency(0f))
  }
}
