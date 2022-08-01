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

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

interface EqPreset {

  @Immutable
  interface BandData : Parcelable {
    val preAmp: Amp
    val bandValues: ImmutableList<Amp>

    fun setPreAmp(amp: Amp): BandData
    fun setBand(band: EqBand, amp: Amp): BandData

    operator fun get(band: EqBand): Amp = bandValues[band.index]

    fun clone(): BandData

    companion object {
      val FLAT: BandData = PresetBandData(
        Amp.NONE,
        List(10) { Amp.NONE }.toPersistentList()
      )

      operator fun invoke(preAmp: Amp, bands: List<Amp>): BandData {
        return PresetBandData(preAmp, bands.toPersistentList())
      }

      private object BandListParceler : Parceler<PersistentList<Amp>> {
        override fun PersistentList<Amp>.write(parcel: Parcel, flags: Int) {
          parcel.writeList(this)
        }

        override fun create(parcel: Parcel): PersistentList<Amp> = ArrayList<Amp>(10).apply {
          parcel.readList(this, Amp::class.java.classLoader)
        }.toPersistentList()
      }

      @Parcelize
      private data class PresetBandData(
        override val preAmp: Amp,
        override val bandValues: @WriteWith<BandListParceler> PersistentList<Amp>
      ) : BandData {
        override fun setPreAmp(amp: Amp): PresetBandData = copy(preAmp = amp)
        override fun setBand(
          band: EqBand,
          amp: Amp
        ): PresetBandData = copy(bandValues = bandValues.set(band.index, amp))
        override fun clone(): BandData = copy()
      }
    }
  }

  /**
   * Id of the preset and should be treated as an opaque value (could be a system ID or an
   * internal persistent ID)
   */
  val id: EqPresetId

  /**
   * True if this preset represents "no preset", equivalent to the Equalizer being off. If this
   * value is true, none of the other preset information should be used.
   */
  val isNone: Boolean
    get() = id.value < 0

  /** This is the opposite of [isNone] */
  val isValid: Boolean
    get() = !isNone

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

  val eqBands: ImmutableList<EqBand>

  /** Current pre-amplification value of this equalizer. */
  val preAmp: Amp

  /**
   * Set this preset's PreAmp [amplitude]. The preamp adjusts the overall gain of the equalizer.
   *
   * Not a var as we need to suspend on set
   * @throws UnsupportedOperationException if this is a system preset
   * @throws IllegalArgumentException if value is not in [Amp.RANGE]
   */
  @Throws(UnsupportedOperationException::class, IllegalArgumentException::class)
  suspend fun setPreAmp(amplitude: Amp)

  /**
   * Get the amplification value for a particular equalizer frequency band.
   * @param band the frequency band to get
   * @return amplification value (Hz)
   */
  fun getAmp(band: EqBand): Amp

  /**
   * Set a new amplification [amplitude] for the equalizer frequency [band].
   * @throws UnsupportedOperationException if this is a system preset
   * @throws IllegalArgumentException if value is not in [Amp.RANGE]
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun setAmp(band: EqBand, amplitude: Amp)

  /**
   * Sets all frequencies to flat and the preamp to it's default value
   * @throws UnsupportedOperationException if this is a system preset
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun resetAllToDefault()

  /**
   * Get the preamp value and all band values in an array
   */
  fun getAllValues(): BandData

  /**
   * Set the preamp and all band values.
   * @throws UnsupportedOperationException if this is a system preset
   */
  @Throws(UnsupportedOperationException::class)
  suspend fun setAllValues(bandData: BandData)

  companion object {
    val BAND_DEFAULT = Amp.NONE

    val NONE = object : EqPreset {
      override val id: EqPresetId = EqPresetId(-1L)
      override val name = "EqPreset.NONE"
      override val displayName = "None"
      override val isSystemPreset = true
      override val eqBands: ImmutableList<EqBand> = emptyList<EqBand>().toImmutableList()
      override val preAmp: Amp = Amp.NONE
      override suspend fun setPreAmp(amplitude: Amp) {}
      override fun getAmp(band: EqBand): Amp = Amp.NONE
      override suspend fun setAmp(band: EqBand, amplitude: Amp) {}
      override suspend fun resetAllToDefault() {}
      override suspend fun setAllValues(bandData: BandData) {}
      override fun getAllValues(): BandData = BandData(
        Amp.NONE,
        List(10) { Amp.NONE }
      )

      override fun toString() = "NONE"
    }
  }
}
