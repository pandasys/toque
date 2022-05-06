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

package com.ealva.toque.service.vlc

import com.ealva.prefstore.store.MutablePreferenceStore
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.StoreHolder
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.service.vlc.Chroma
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.service.vlc.SkipLoopFilter
import com.ealva.toque.service.vlc.SubtitleEncoding
import com.ealva.toque.test.prefs.PrefStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.ealva.toque.service.vlc.HardwareAcceleration as HardwareAccel

typealias BoolPrefStub = PrefStub<Boolean, Boolean>
typealias MillisPrefStub = PrefStub<Long, Millis>
typealias AmpPrefStub = PrefStub<Float, Amp>

class StubLibVlcPrefs : LibVlcPrefs {
  override val enableVerboseMode = BoolPrefStub(false)
  override val audioOutputModule = PrefStub<Int, AudioOutputModule>(AudioOutputModule.DEFAULT)
  override val chroma = PrefStub<Int, Chroma>(Chroma.DEFAULT)
  override val networkCachingAmount = MillisPrefStub(Millis.ONE_SECOND)
  override val subtitleEncoding = PrefStub<Int, SubtitleEncoding>(SubtitleEncoding.DEFAULT)
  override val replayGainMode = PrefStub<Int, ReplayGainMode>(ReplayGainMode.DEFAULT)
  override val replayGainPreamp = AmpPrefStub(Amp(0))
  override val defaultReplayGain = AmpPrefStub(Amp(-7))
  override val enableFrameSkip = BoolPrefStub(false)
  override val skipLoopFilter = PrefStub<Int, SkipLoopFilter>(SkipLoopFilter.DEFAULT)
  override val allowTimeStretchAudio = BoolPrefStub(true)
  override val digitalAudioOutputEnabled = BoolPrefStub(false)
  override val hardwareAcceleration = PrefStub<Int, HardwareAccel>(HardwareAccel.DEFAULT)
  override val updateFlow: Flow<StoreHolder<LibVlcPrefs>> = emptyFlow()
  override suspend fun clear(vararg prefs: PreferenceStore.Preference<*, *>) = Unit
  override suspend fun edit(block: suspend LibVlcPrefs.(MutablePreferenceStore) -> Unit) = Unit
}
