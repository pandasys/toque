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

package com.ealva.toque.android.prefs

import com.ealva.toque.service.vlc.Chroma
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

  var networkCachingMillis = 1000
  override fun networkCachingMillis(): Int = networkCachingMillis
  override suspend fun networkCachingMillis(millis: Int): Boolean {
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

  var replayGainPreamp = 0F
  override fun replayGainPreamp(): Float = replayGainPreamp
  override suspend fun replayGainPreamp(preamp: Float): Boolean {
    replayGainPreamp = preamp
    return true
  }

  var replayGainDefaultPreamp = 0F
  override fun replayGainDefaultPreamp(): Float = replayGainDefaultPreamp
  override suspend fun replayGainDefaultPreamp(preamp: Float): Boolean {
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
}
