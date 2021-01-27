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

package com.ealva.toque.service.vlc

import android.content.Context
import androidx.datastore.DataStore
import androidx.datastore.preferences.Preferences
import com.ealva.toque.BuildConfig
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.common.MillisRange
import com.ealva.toque.common.toAmp
import com.ealva.toque.common.toMillis
import com.ealva.toque.prefs.EnumKeyDefault
import com.ealva.toque.prefs.KeyDefault
import com.ealva.toque.prefs.get
import com.ealva.toque.prefs.makeDataStore
import com.ealva.toque.prefs.put
import com.ealva.toque.prefs.set
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ALLOW_TIME_STRETCH
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_DEFAULT_REPLAY_GAIN
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_DIGITAL_AUDIO_OUTPUT
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_FRAME_SKIP
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_VERBOSE_MODE
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_NETWORK_CACHING
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_REPLAY_PREAMP
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.NETWORK_CACHING_RANGE
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.ALLOW_TIME_STRETCH_AUDIO
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.ENABLE_DIGITAL_AUDIO_OUTPUT
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.ENABLE_FRAME_SKIP
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.ENABLE_VERBOSE_MODE
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.HARDWARE_ACCELERATION
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.NETWORK_CACHING_MILLISECONDS
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.REPLAY_GAIN_DEFAULT_PREAMP
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.REPLAY_GAIN_MODE
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.REPLAY_GAIN_PREAMP
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.SKIP_LOOP_FILTER
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.SUBTITLE_ENCODING
import com.ealva.toque.service.vlc.LibVlcPreferencesImpl.Keys.VIDEO_CHROMA
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DATA_STORE_FILE_NAME = "LibVLCOptions"

class LibVlcPreferencesSingleton(
  private val context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  /**
   * @throws IllegalStateException if error during LibVLC initialization
   */
  private suspend fun make(): LibVlcPreferences {
    val dataStore = context.makeDataStore(DATA_STORE_FILE_NAME, dispatcher)
    val sharedFlow = dataStore.data.stateIn(CoroutineScope(dispatcher + SupervisorJob()))
    return LibVlcPreferencesImpl(dataStore, sharedFlow)
  }

  @Volatile
  private var instance: LibVlcPreferences? = null
  private val mutex = Mutex()

  /**
   * Get the single instance, which may need to be constructed
   *
   * @throws IllegalStateException if error during LibVLC initialization
   */
  suspend fun instance(): LibVlcPreferences = instance ?: withContext(dispatcher) {
    mutex.withLock { instance ?: make().also { instance = it } }
  }
}

interface LibVlcPreferences {
  val debugAndLogging: Boolean

  fun enableVerboseMode(): Boolean
  suspend fun enableVerboseMode(verbose: Boolean): Boolean

  fun chroma(): Chroma
  suspend fun chroma(chroma: Chroma): Boolean

  fun networkCachingAmount(): Millis
  suspend fun networkCachingAmount(millis: Millis): Boolean

  fun subtitleEncoding(): SubtitleEncoding
  suspend fun subtitleEncoding(encoding: SubtitleEncoding): Boolean

  fun replayGainMode(): ReplayGainMode
  suspend fun replayGainMode(mode: ReplayGainMode): Boolean

  fun replayPreamp(): Amp
  suspend fun replayPreamp(preamp: Amp): Boolean

  fun defaultReplayGain(): Amp
  suspend fun defaultReplayGain(preamp: Amp): Boolean

  fun enableFrameSkip(): Boolean
  suspend fun enableFrameSkip(enable: Boolean): Boolean

  fun skipLoopFilter(): SkipLoopFilter
  suspend fun skipLoopFilter(filter: SkipLoopFilter): Boolean

  fun allowTimeStretchAudio(): Boolean
  suspend fun allowTimeStretchAudio(allow: Boolean): Boolean

  fun digitalAudioOutputEnabled(): Boolean
  suspend fun digitalAudioOutputEnabled(enabled: Boolean): Boolean

  fun hardwareAcceleration(): HardwareAcceleration
  suspend fun hardwareAcceleration(acceleration: HardwareAcceleration): Boolean

  suspend fun resetAllToDefault()

  companion object {
    const val DEFAULT_ENABLE_VERBOSE_MODE = false
    val DEFAULT_NETWORK_CACHING = 1000.toMillis()
    val NETWORK_CACHING_RANGE: MillisRange = Millis.ZERO..Millis.ONE_MINUTE
    const val SUB_AUTODETECT_PATHS = "./Subtitles, ./subtitles, ./Subs, ./subs"
    val DEFAULT_REPLAY_PREAMP = Amp.ZERO
    val DEFAULT_DEFAULT_REPLAY_GAIN = (-7).toAmp()
    const val DEFAULT_ENABLE_FRAME_SKIP = false
    const val DEFAULT_ALLOW_TIME_STRETCH = true
    const val DEFAULT_ENABLE_DIGITAL_AUDIO_OUTPUT = false
  }
}

/**
 * Get/set LibVLC preferences. Get methods return the default value if null (not set)
 */
private class LibVlcPreferencesImpl(
  private val dataStore: DataStore<Preferences>,
  private val state: StateFlow<Preferences>
) : LibVlcPreferences {

  object Keys {
    val ENABLE_VERBOSE_MODE = KeyDefault("enable_verbose_mode", DEFAULT_ENABLE_VERBOSE_MODE)
    val VIDEO_CHROMA = EnumKeyDefault(Chroma.DEFAULT)
    val NETWORK_CACHING_MILLISECONDS =
      KeyDefault("network_caching", DEFAULT_NETWORK_CACHING, ::Millis, { it.value })
    val SUBTITLE_ENCODING = EnumKeyDefault(SubtitleEncoding.DEFAULT)
    val REPLAY_GAIN_MODE = EnumKeyDefault(ReplayGainMode.DEFAULT)
    val REPLAY_GAIN_PREAMP =
      KeyDefault("replay_gain_preamp", DEFAULT_REPLAY_PREAMP, ::Amp, { it.value })
    val REPLAY_GAIN_DEFAULT_PREAMP =
      KeyDefault("replay_gain_default_preamp", DEFAULT_DEFAULT_REPLAY_GAIN, ::Amp, { it.value })
    val ENABLE_FRAME_SKIP = KeyDefault("enable_frame_skip", DEFAULT_ENABLE_FRAME_SKIP)
    val SKIP_LOOP_FILTER = EnumKeyDefault(SkipLoopFilter.DEFAULT)
    val ALLOW_TIME_STRETCH_AUDIO =
      KeyDefault("allow_time_stretch_audio", DEFAULT_ALLOW_TIME_STRETCH)
    val ENABLE_DIGITAL_AUDIO_OUTPUT =
      KeyDefault("enable_digital_audio_output", DEFAULT_ENABLE_DIGITAL_AUDIO_OUTPUT)
    val HARDWARE_ACCELERATION = EnumKeyDefault(HardwareAcceleration.DEFAULT)
  }

  override val debugAndLogging: Boolean
    get() = BuildConfig.DEBUG && BuildConfig.VLC_LOGGING

  override fun enableVerboseMode(): Boolean = state.value[ENABLE_VERBOSE_MODE]
  override suspend fun enableVerboseMode(verbose: Boolean): Boolean =
    dataStore.set(ENABLE_VERBOSE_MODE, verbose)

  override fun chroma(): Chroma = state.value[VIDEO_CHROMA]
  override suspend fun chroma(chroma: Chroma): Boolean = dataStore.set(VIDEO_CHROMA, chroma)

  override fun networkCachingAmount(): Millis = state.value[NETWORK_CACHING_MILLISECONDS]
  override suspend fun networkCachingAmount(millis: Millis): Boolean =
    dataStore.set(NETWORK_CACHING_MILLISECONDS, millis.coerceIn(NETWORK_CACHING_RANGE))

  override fun subtitleEncoding() = state.value[SUBTITLE_ENCODING]
  override suspend fun subtitleEncoding(encoding: SubtitleEncoding): Boolean =
    dataStore.set(SUBTITLE_ENCODING, encoding)

  override fun replayGainMode() = state.value[REPLAY_GAIN_MODE]
  override suspend fun replayGainMode(mode: ReplayGainMode) =
    dataStore.set(REPLAY_GAIN_MODE, mode)

  override fun replayPreamp(): Amp = state.value[REPLAY_GAIN_PREAMP]
  override suspend fun replayPreamp(preamp: Amp): Boolean =
    dataStore.set(REPLAY_GAIN_PREAMP, preamp.coerceIn(EqPreset.AMP_RANGE))

  override fun defaultReplayGain(): Amp = state.value[REPLAY_GAIN_DEFAULT_PREAMP]
  override suspend fun defaultReplayGain(preamp: Amp): Boolean =
    dataStore.set(REPLAY_GAIN_DEFAULT_PREAMP, preamp.coerceIn(EqPreset.AMP_RANGE))

  override fun enableFrameSkip(): Boolean = state.value[ENABLE_FRAME_SKIP]
  override suspend fun enableFrameSkip(enable: Boolean): Boolean =
    dataStore.set(ENABLE_FRAME_SKIP, enable)

  override fun skipLoopFilter() = state.value[SKIP_LOOP_FILTER]
  override suspend fun skipLoopFilter(filter: SkipLoopFilter): Boolean =
    dataStore.set(SKIP_LOOP_FILTER, filter)

  override fun allowTimeStretchAudio(): Boolean = state.value[ALLOW_TIME_STRETCH_AUDIO]
  override suspend fun allowTimeStretchAudio(allow: Boolean): Boolean =
    dataStore.set(ALLOW_TIME_STRETCH_AUDIO, allow)

  override fun digitalAudioOutputEnabled(): Boolean = state.value[ENABLE_DIGITAL_AUDIO_OUTPUT]
  override suspend fun digitalAudioOutputEnabled(enabled: Boolean): Boolean =
    dataStore.set(ENABLE_DIGITAL_AUDIO_OUTPUT, enabled)

  override fun hardwareAcceleration(): HardwareAcceleration = state.value[HARDWARE_ACCELERATION]
  override suspend fun hardwareAcceleration(acceleration: HardwareAcceleration): Boolean =
    dataStore.set(HARDWARE_ACCELERATION, acceleration)

  override suspend fun resetAllToDefault() {
    dataStore.put {
      this[ENABLE_VERBOSE_MODE.key] =
        ENABLE_VERBOSE_MODE.realToStored(ENABLE_VERBOSE_MODE.defaultValue)
      this[VIDEO_CHROMA.key] = VIDEO_CHROMA.realToStored(VIDEO_CHROMA.defaultValue)
      this[NETWORK_CACHING_MILLISECONDS.key] =
        NETWORK_CACHING_MILLISECONDS.realToStored(NETWORK_CACHING_MILLISECONDS.defaultValue)
      this[SUBTITLE_ENCODING.key] = SUBTITLE_ENCODING.realToStored(SUBTITLE_ENCODING.defaultValue)
      this[REPLAY_GAIN_MODE.key] = REPLAY_GAIN_MODE.realToStored(REPLAY_GAIN_MODE.defaultValue)
      this[REPLAY_GAIN_PREAMP.key] =
        REPLAY_GAIN_PREAMP.realToStored(REPLAY_GAIN_PREAMP.defaultValue)
      this[REPLAY_GAIN_DEFAULT_PREAMP.key] =
        REPLAY_GAIN_DEFAULT_PREAMP.realToStored(REPLAY_GAIN_DEFAULT_PREAMP.defaultValue)
      this[ENABLE_FRAME_SKIP.key] = ENABLE_FRAME_SKIP.realToStored(ENABLE_FRAME_SKIP.defaultValue)
      this[SKIP_LOOP_FILTER.key] = SKIP_LOOP_FILTER.realToStored(SKIP_LOOP_FILTER.defaultValue)
      this[ALLOW_TIME_STRETCH_AUDIO.key] =
        ALLOW_TIME_STRETCH_AUDIO.realToStored(ALLOW_TIME_STRETCH_AUDIO.defaultValue)
      this[ENABLE_DIGITAL_AUDIO_OUTPUT.key] =
        ENABLE_DIGITAL_AUDIO_OUTPUT.realToStored(ENABLE_DIGITAL_AUDIO_OUTPUT.defaultValue)
      this[HARDWARE_ACCELERATION.key] =
        HARDWARE_ACCELERATION.realToStored(HARDWARE_ACCELERATION.defaultValue)
    }
  }
}
