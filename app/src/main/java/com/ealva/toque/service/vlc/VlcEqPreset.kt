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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Frequency
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.db.EqPresetData
import com.ealva.toque.db.NullEqPresetDao
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPreset.BandData
import com.ealva.toque.service.media.EqPreset.Companion.BAND_DEFAULT
import com.github.michaelbull.result.onFailure
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.videolan.libvlc.MediaPlayer

@Suppress("unused")
private val LOG by lazyLogger(VlcEqPreset::class)

class VlcEqPreset private constructor(
  private val nativeEq: MediaPlayer.Equalizer?,
  private var realName: String,
  override val isSystemPreset: Boolean,
  override val id: EqPresetId,
  private val eqPresetDao: EqPresetDao
) : EqPreset {

  override val name: String
    get() = realName

  override val displayName: String = if (isSystemPreset) "*$name" else name

  override val eqBands: ImmutableList<EqBand>
    get() = EQ_BANDS

  override val preAmp: Amp
    get() = nativeEq?.preAmp?.let { Amp(it) } ?: Amp.NONE

  private suspend fun persistPresetData() {
    eqPresetDao.updatePreset(EqPresetData(id, name, getPreAmpAndBands()))
      .onFailure { cause -> LOG.e(cause) { it("Could not update $name $id") } }
  }

  private fun getPreAmpAndBands(): EqPresetDao.PreAmpAndBands = nativeEq?.let { native ->
    EqPresetDao.PreAmpAndBands(
      Amp(native.preAmp),
      Array(EQ_BANDS.size) { index -> getAmp(EQ_BANDS[index]) }
    )
  } ?: EqPresetDao.PreAmpAndBands(Amp.NONE, ZEROED_BANDS.toTypedArray())

  override suspend fun setPreAmp(amplitude: Amp) {
    checkNotSystemPreset()
    require(amplitude in Amp.RANGE)
    nativeEq?.let { native ->
      native.preAmp = amplitude.value
      persistPresetData()
    }
  }

  override fun getAmp(band: EqBand): Amp {
    return nativeEq?.getAmp(band.index)?.let { Amp(it) } ?: Amp.NONE
  }

  override suspend fun setAmp(band: EqBand, amplitude: Amp) {
    checkNotSystemPreset()
    require(amplitude in Amp.RANGE)
    nativeEq?.let { native ->
      native.setAmp(band.index, amplitude.value)
      persistPresetData()
    }
  }

  override suspend fun resetAllToDefault() {
    checkNotSystemPreset()
    nativeEq?.let { native ->
      EQ_BANDS.forEach { band -> native.setAmp(band.index, BAND_DEFAULT.value) }
      native.preAmp = Amp.DEFAULT_PREAMP.value
      persistPresetData()
    }
  }

  override fun getAllValues(): BandData {
    return nativeEq?.let { native ->
      BandData(
        Amp(native.preAmp),
        List(EQ_BANDS.size) { index ->
          getAmp(EQ_BANDS[index])
        }
      )
    } ?: BandData(Amp.NONE, ZEROED_BANDS)
  }

  override suspend fun setAllValues(bandData: BandData) {
    checkNotSystemPreset()
    nativeEq?.let { native ->
      native.setNativeEqValues(bandData)
      persistPresetData()
    }
  }

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

    fun MediaPlayer.Equalizer.setNativeEqValues(preAmpAndBands: BandData) = apply {
      require(preAmpAndBands.preAmp in Amp.RANGE)
      preAmp = preAmpAndBands.preAmp.value
      preAmpAndBands.bandValues.forEachIndexed { index, amp ->
        require(amp in Amp.RANGE)
        setAmp(index, amp.value)
      }
    }

    /**
     * The [NONE] preset represents no preset being set into the media player. In other words, it
     * sets ```null``` into the media player, it is considered a system preset so can't be edited,
     * and returns default values of 0F for preAmp and amp
     */
    val NONE = VlcEqPreset(null, "None", true, EqPresetId(-1), NullEqPresetDao)

    private val ZEROED_BANDS: ImmutableList<Amp> =
      List(MediaPlayer.Equalizer.getBandCount()) { Amp.NONE }.toImmutableList()

    private val EQ_BANDS: ImmutableList<EqBand> = List(MediaPlayer.Equalizer.getBandCount()) { i ->
      EqBand(i, getBandFrequency(i))
    }.toImmutableList()

    private fun getBandFrequency(index: Int): Frequency =
      Frequency(MediaPlayer.Equalizer.getBandFrequency(index))
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
