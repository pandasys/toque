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

package com.ealva.toque.service.vlc

import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.db.EqPresetData
import com.ealva.toque.db.NullEqPresetDao
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.Companion.BAND_DEFAULT
import com.ealva.toque.service.media.PreAmpAndBands
import com.github.michaelbull.result.onFailure
import org.videolan.libvlc.MediaPlayer

private val ZEROED_BANDS: Array<Amp> = Array(VlcEqPreset.BAND_COUNT) { Amp.NONE }

class VlcEqPreset private constructor(
  private val nativeEq: MediaPlayer.Equalizer?,
  override val name: String,
  override val isSystemPreset: Boolean,
  override val id: EqPresetId,
  private val eqPresetDao: EqPresetDao
) : EqPreset {
  override val isNullPreset: Boolean = nativeEq == null

  override val displayName: String = if (isSystemPreset) "*$name" else name

  override val bandCount: Int
    get() = BAND_COUNT

  override val bandIndices: IntRange
    get() = BAND_INDICES

  override fun getBandFrequency(index: Int): Float = MediaPlayer.Equalizer.getBandFrequency(index)

  override fun get(index: Int): Float  = MediaPlayer.Equalizer.getBandFrequency(index)

  override val preAmp: Amp
    get() = nativeEq?.preAmp?.let { Amp(it) } ?: Amp.NONE

  override suspend fun setPreAmp(amplitude: Amp) {
    checkNotSystemPreset()
    require(amplitude in Amp.RANGE)
    nativeEq?.preAmp = amplitude()
  }

  override fun getAmp(index: Int): Amp {
    require(index in BAND_INDICES)
    return nativeEq?.getAmp(index)?.let { Amp(it) } ?: Amp.NONE
  }

  override suspend fun setAmp(index: Int, amplitude: Amp) {
    checkNotSystemPreset()
    require(amplitude in Amp.RANGE)
    require(index in BAND_INDICES)
    nativeEq?.setAmp(index, amplitude())
  }

  override suspend fun resetAllToDefault() {
    checkNotSystemPreset()
    nativeEq?.let { native ->
      val currentValues = getAllValues()
      try {
        for (i in BAND_INDICES) native.setAmp(i, BAND_DEFAULT())
        native.preAmp = Amp.DEFAULT_PREAMP.value
      } catch (e: Exception) {
        native.setNativeEqValues(currentValues)
      }
    }
  }

  override fun getAllValues(): PreAmpAndBands {
    return nativeEq?.let { native ->
      PreAmpAndBands(
        Amp(native.preAmp),
        Array(BAND_COUNT) { index ->
          Amp(native.getAmp(index))
        }
      )
    } ?: PreAmpAndBands(Amp.NONE, ZEROED_BANDS)
  }

  override suspend fun setAllValues(preAmpAndBands: PreAmpAndBands) {
    checkNotSystemPreset()
    nativeEq?.let { native ->
      val currentValues: PreAmpAndBands = getAllValues()
      eqPresetDao.updatePreset(EqPresetData(id, name, preAmpAndBands)) {
        native.setNativeEqValues(preAmpAndBands)
      }.onFailure {
        native.setNativeEqValues(currentValues)
      }
    }
  }

  override fun clone(): EqPreset = VlcEqPreset(nativeEq, name, isSystemPreset, id, eqPresetDao)

  fun applyToPlayer(nativePlayer: MediaPlayer): Boolean = nativePlayer.setEqualizer(nativeEq)

  override fun toString(): String = name

  companion object {
    operator fun invoke(
      nativeEq: MediaPlayer.Equalizer,
      name: String,
      isSystemPreset: Boolean,
      id: EqPresetId,
      eqPresetDao: EqPresetDao
    ): VlcEqPreset = VlcEqPreset(nativeEq, name, isSystemPreset, id, eqPresetDao)

    fun MediaPlayer.Equalizer.setNativeEqValues(preAmpAndBands: PreAmpAndBands) = apply {
      require(preAmpAndBands.preAmp in Amp.RANGE)
      preAmp = preAmpAndBands.preAmp()
      preAmpAndBands.bands.forEachIndexed { index, amp ->
        require(amp in Amp.RANGE)
        setAmp(index, amp())
      }
    }

    /**
     * The [NONE] preset represents no preset being set into the media player. In other words, it
     * sets ```null``` into the media player, it is considered a system preset so can't be edited,
     * and returns default values of 0F for preAmp and amp
     */
    val NONE = VlcEqPreset(null, "None", true, EqPresetId(-1), NullEqPresetDao)

    val BAND_COUNT = MediaPlayer.Equalizer.getBandCount()
    val BAND_INDICES = 0 until BAND_COUNT
  }
}

inline fun VlcEqPreset.checkNotSystemPreset(
  lazyMessage: () -> String = { "Cannot edit system preset." }
) {
  if (isSystemPreset) {
    throw UnsupportedOperationException(lazyMessage())
  }
}

fun EqPreset.asVlcPreset(): VlcEqPreset = if (this is VlcEqPreset) this else VlcEqPreset.NONE
