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

package com.ealva.toque.test.db

import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Frequency
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class EqPresetStub(
  override var id: EqPresetId = EqPresetId(0),
  private val defaultBandValues: Array<Amp> = Array(10) { Amp.NONE }
) : EqPreset {
  override var name: String = "EqPresetStub"
  override val displayName: String = name
  override var isSystemPreset: Boolean = false
  private val bandFrequencies =
    floatArrayOf(31F, 63F, 125F, 250F, 500F, 1000F, 2000F, 4000F, 8000F, 16000F)
  override val eqBands: ImmutableList<EqBand> = buildList<EqBand> {
    bandFrequencies.forEachIndexed { index, frequency ->
      EqBand(index, Frequency(frequency))
    }
  }.toImmutableList()

  override var preAmp: Amp = Amp.DEFAULT_PREAMP

  override suspend fun setPreAmp(amplitude: Amp) {
    preAmp = amplitude
  }

  private var bandValues: Array<Amp> = defaultBandValues
  override fun getAmp(band: EqBand): Amp = bandValues[band.index]

  override suspend fun setAmp(band: EqBand, amplitude: Amp) {
    bandValues[band.index] = amplitude
  }

  override suspend fun resetAllToDefault() {
    preAmp = Amp.DEFAULT_PREAMP
    bandValues = defaultBandValues
  }

  override fun getAllValues(): BandData = BandData(preAmp, bandValues.toList())

  override suspend fun setAllValues(bandData: BandData) {
    preAmp = bandData.preAmp
    bandValues = bandData.bandValues.toTypedArray()
  }
}
