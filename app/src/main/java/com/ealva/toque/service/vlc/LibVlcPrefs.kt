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

package com.ealva.toque.service.vlc

import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.common.MillisRange
import com.ealva.toque.prefs.AmpStorePref
import com.ealva.toque.prefs.BaseToquePreferenceStore
import com.ealva.toque.prefs.MillisStorePref
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.vlc.LibVlcPrefs.Companion.NETWORK_CACHING_RANGE

typealias LibVlcPrefsSingleton = PreferenceStoreSingleton<LibVlcPrefs>

interface LibVlcPrefs : PreferenceStore<LibVlcPrefs> {
  val debugAndLogging: Boolean
  val enableVerboseMode: BoolPref
  val chroma: StorePref<Int, Chroma>
  val networkCachingAmount: MillisStorePref
  val subtitleEncoding: StorePref<Int, SubtitleEncoding>
  val replayGainMode: StorePref<Int, ReplayGainMode>
  val replayPreamp: AmpStorePref
  val defaultReplayGain: AmpStorePref
  val enableFrameSkip: BoolPref
  val skipLoopFilter: StorePref<Int, SkipLoopFilter>
  val allowTimeStretchAudio: BoolPref
  val digitalAudioOutputEnabled: BoolPref
  val hardwareAcceleration: StorePref<Int, HardwareAcceleration>

  companion object {
    val NETWORK_CACHING_RANGE: MillisRange = Millis.ZERO..Millis.ONE_MINUTE
    const val SUB_AUTODETECT_PATHS = "./Subtitles, ./subtitles, ./Subs, ./subs"

    fun make(storage: Storage): LibVlcPrefs = LibVlcPrefsImpl(storage)
  }
}

private class LibVlcPrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<LibVlcPrefs>(storage), LibVlcPrefs {
  override val debugAndLogging: Boolean = true
  override val enableVerboseMode by preference(false)
  override val chroma by enumPref(Chroma.DEFAULT)
  override val networkCachingAmount by millisPref(Millis.ONE_SECOND) {
    it.coerceIn(NETWORK_CACHING_RANGE)
  }
  override val subtitleEncoding by enumPref(SubtitleEncoding.DEFAULT)
  override val replayGainMode by enumPref(ReplayGainMode.DEFAULT)
  override val replayPreamp by ampPref(Amp.ZERO) {
    it.coerceIn(EqPreset.AMP_RANGE)
  }

  @Suppress("MagicNumber")
  override val defaultReplayGain by ampPref(Amp(-7F)) {
    it.coerceIn(EqPreset.AMP_RANGE)
  }
  override val enableFrameSkip by preference(false)
  override val skipLoopFilter by enumPref(SkipLoopFilter.DEFAULT)
  override val allowTimeStretchAudio by preference(true)
  override val digitalAudioOutputEnabled by preference(false)
  override val hardwareAcceleration by enumPref(HardwareAcceleration.DEFAULT)
}
