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

package com.ealva.toque.service.media

import com.ealva.toque.common.Amp
import com.ealva.toque.common.toAmp

data class PreAmpAndBands(val preAmp: Amp, val bands: Array<Amp>) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PreAmpAndBands

    if (preAmp != other.preAmp) return false
    if (!bands.contentEquals(other.bands)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = preAmp.hashCode()
    result = 31 * result + bands.contentHashCode()
    return result
  }

  override fun toString(): String {
    return """PreAmpAndBands[$preAmp, ${bands.contentToString()}]"""
  }
}

interface EqPreset {
  /**
   * Id of the preset and should be treated as an opaque value (could be a system ID or an
   * internal persistent ID)
   */
  val presetId: Long

  /**
   * Get the name of this equalizer preset, which is either a system preset or user assigned name.
   */
  val name: String

  /**
   * Name to be displayed in the UI. Only current difference from [name] is that system presets have
   * an "*" prepended to indicate to the user they are system cannot be edited
   */
  val displayName: String

  /** System presets are not editable and are not persisted */
  val isSystemPreset: Boolean

  /** Get the number of distinct frequency bands for an equalizer */
  val bandCount: Int

  val bandIndices: IntRange

  /**
   * Get a particular equalizer band frequency. This value can be used, for example, to create a
   * label for an equalizer band control in a user interface.
   * @param index index of the band, counting from zero.
   * @return equalizer band frequency (Hz), or -1 if there is no such band
   */
  fun getBandFrequency(index: Int): Float

  /** Current pre-amplification value of this equalizer. */
  val preAmp: Amp

  /**
   * Set this preset's PreAmp [amplitude]. Not a var as we need to suspend on set
   * @throws UnsupportedOperationException if this is a system preset
   * @throws IllegalArgumentException if value is not in [AMP_RANGE]
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun setPreAmp(amplitude: Amp)

  /**
   * Get the amplification value for a particular equalizer frequency band.
   * @param index counting from zero, of the frequency band to get.
   * @return amplification value (Hz); NaN if there is no such frequency band.
   */
  fun getAmp(index: Int): Amp

  /**
   * Set a new amplification [amplitude] for the equalizer frequency band at [index].
   * @throws UnsupportedOperationException if this is a system preset
   * @throws IllegalArgumentException if value is not in [AMP_RANGE]
   * @throws IllegalArgumentException if index is not in [bandIndices]
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun setAmp(index: Int, amplitude: Amp)

  /**
   * Sets all frequencies to flat and the preamp to it's default value
   * @throws UnsupportedOperationException if this is a system preset
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun resetAllToDefault()

  /**
   * Get the preamp value and all band values in an array
   */
  fun getAllValues(): PreAmpAndBands

  /**
   * Set the preamp and all band values. The size of the amp array must be equal to [bandCount].
   * Oh, and this one time at band camp...
   * @throws UnsupportedOperationException if this is a system preset
   * @throws IllegalArgumentException if value is not in [AMP_RANGE]
   * @throws IllegalArgumentException if index is not in [bandIndices]
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun setAllValues(preAmpAndBands: PreAmpAndBands)

  companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    val MAX_AMP = 20F.toAmp()
    @Suppress("MemberVisibilityCanBePrivate")
    val MIN_AMP = (-20F).toAmp()
    val AMP_RANGE = MIN_AMP..MAX_AMP
    val PRE_AMP_DEFAULT = 12F.toAmp()
    val BAND_DEFAULT = 0F.toAmp()
  }
}
