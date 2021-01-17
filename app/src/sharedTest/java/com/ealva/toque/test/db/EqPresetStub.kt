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

package com.ealva.toque.test.db

import com.ealva.toque.common.Amp
import com.ealva.toque.common.toAmp
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.Companion.PRE_AMP_DEFAULT
import com.ealva.toque.service.media.PreAmpAndBands

private val ZERO_AMP = 0.toAmp()

class EqPresetStub(override var presetId: Long = 0) : EqPreset {
  override var name: String = "EqPresetStub"
  override val displayName: String = name
  override var isSystemPreset: Boolean = false
  override val bandCount: Int = 10
  override val bandIndices: IntRange = 0 until bandCount

  private val bandFrequencies =
    floatArrayOf(31F, 63F, 125F, 250F, 500F, 1000F, 2000F, 4000F, 8000F, 16000F)

  override fun getBandFrequency(index: Int): Float = bandFrequencies[index]

  override var preAmp: Amp = 12F.toAmp()
  override suspend fun setPreAmp(amplitude: Amp) {
    preAmp = amplitude
  }

  private val defaultBandValues: Array<Amp> = arrayOf(
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP,
    ZERO_AMP
  )
  private var bandValues: Array<Amp> = defaultBandValues
  override fun getAmp(index: Int): Amp = bandValues[index]

  override suspend fun setAmp(index: Int, amplitude: Amp) {
    bandValues[index] = amplitude
  }

  override suspend fun resetAllToDefault() {
    preAmp = PRE_AMP_DEFAULT
    bandValues = defaultBandValues
  }

  override fun getAllValues(): PreAmpAndBands {
    return PreAmpAndBands(preAmp, bandValues)
  }

  override suspend fun setAllValues(preAmpAndBands: PreAmpAndBands) {
    preAmp = preAmpAndBands.preAmp
    bandValues = preAmpAndBands.bands
  }
}
