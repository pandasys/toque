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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.ealva.toque.android.prefs

import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.common.toAmp
import com.ealva.toque.common.toMillis
import com.ealva.toque.service.vlc.Chroma
import com.ealva.toque.service.vlc.HardwareAcceleration
import com.ealva.toque.service.vlc.LibVlcPreferences
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.service.vlc.SkipLoopFilter
import com.ealva.toque.service.vlc.SubtitleEncoding

class StubLibVlcPreferences : LibVlcPreferences {
  var enableVerboseMode = false

  override var debugAndLogging: Boolean = false

  override fun enableVerboseMode(): Boolean = enableVerboseMode
  override suspend fun enableVerboseMode(verbose: Boolean): Boolean {
    enableVerboseMode = verbose
    return true
  }

  var chroma = Chroma.DEFAULT
  override fun chroma(): Chroma = chroma
  override suspend fun chroma(chroma: Chroma): Boolean {
    this.chroma = chroma
    return true
  }

  var networkCachingMillis = 1000.toMillis()
  override fun networkCachingAmount(): Millis = networkCachingMillis
  override suspend fun networkCachingAmount(millis: Millis): Boolean {
    networkCachingMillis = millis
    return true
  }

  var subtitleEncoding = SubtitleEncoding.DEFAULT
  override fun subtitleEncoding(): SubtitleEncoding = subtitleEncoding
  override suspend fun subtitleEncoding(encoding: SubtitleEncoding): Boolean {
    subtitleEncoding = encoding
    return true
  }

  var replayGainMode = ReplayGainMode.DEFAULT
  override fun replayGainMode(): ReplayGainMode = replayGainMode
  override suspend fun replayGainMode(mode: ReplayGainMode): Boolean {
    replayGainMode = mode
    return true
  }

  var replayGainPreamp = 0F.toAmp()
  override fun replayGainPreamp(): Amp = replayGainPreamp
  override suspend fun replayGainPreamp(preamp: Amp): Boolean {
    replayGainPreamp = preamp
    return true
  }

  var replayGainDefaultPreamp = 0F.toAmp()
  override fun replayGainDefaultPreamp(): Amp = replayGainDefaultPreamp
  override suspend fun replayGainDefaultPreamp(preamp: Amp): Boolean {
    replayGainDefaultPreamp = preamp
    return true
  }

  var enableFrameSkip = false
  override fun enableFrameSkip(): Boolean = enableFrameSkip
  override suspend fun enableFrameSkip(enable: Boolean): Boolean {
    enableFrameSkip = enable
    return true
  }

  var skipLoopFilter = SkipLoopFilter.DEFAULT
  override fun skipLoopFilter(): SkipLoopFilter = skipLoopFilter
  override suspend fun skipLoopFilter(filter: SkipLoopFilter): Boolean {
    skipLoopFilter = filter
    return true
  }

  var allowTimeStretchAudio = true
  override fun allowTimeStretchAudio(): Boolean = allowTimeStretchAudio
  override suspend fun allowTimeStretchAudio(allow: Boolean): Boolean {
    allowTimeStretchAudio = allow
    return true
  }

  var digitalAudioOutputEnabled = false
  override fun digitalAudioOutputEnabled(): Boolean = digitalAudioOutputEnabled
  override suspend fun digitalAudioOutputEnabled(enabled: Boolean): Boolean {
    digitalAudioOutputEnabled = enabled
    return true
  }

  var hardwareAcceleration = HardwareAcceleration.AUTOMATIC
  override fun hardwareAcceleration(): HardwareAcceleration = hardwareAcceleration
  override suspend fun hardwareAcceleration(acceleration: HardwareAcceleration): Boolean {
    hardwareAcceleration = acceleration
    return true
  }
}
