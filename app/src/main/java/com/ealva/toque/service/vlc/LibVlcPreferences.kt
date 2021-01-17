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
import androidx.datastore.preferences.preferencesKey
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.BuildConfig
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.common.MillisRange
import com.ealva.toque.common.toAmp
import com.ealva.toque.common.toMillis
import com.ealva.toque.prefs.get
import com.ealva.toque.prefs.makeDataStore
import com.ealva.toque.prefs.set
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_NETWORK_CACHING
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

private val LOG by lazyLogger(LibVlcPreferences::class)

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
  suspend fun instance(): LibVlcPreferences {
    instance?.let { return it } ?: return withContext(dispatcher) {
      mutex.withLock {
        instance?.let { instance } ?: make().also { instance = it }
      }
    }
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

  fun replayGainPreamp(): Amp
  suspend fun replayGainPreamp(preamp: Amp): Boolean

  fun replayGainDefaultPreamp(): Amp
  suspend fun replayGainDefaultPreamp(preamp: Amp): Boolean

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

  companion object {
    const val DEFAULT_NETWORK_CACHING = 1000L
    val NETWORK_CACHING_RANGE: MillisRange = Millis.ZERO..Millis.ONE_MINUTE
    const val SUB_AUTODETECT_PATHS = "./Subtitles, ./subtitles, ./Subs, ./subs"
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
    val ENABLE_VERBOSE_MODE = preferencesKey<Boolean>("enable_verbose_mode")
    val VIDEO_CHROMA = preferencesKey<Int>("video_chroma")
    val NETWORK_CACHING_MILLISECONDS = preferencesKey<Long>("network_caching")
    val SUBTITLE_ENCODING = preferencesKey<Int>("subtitle_encoding")
    val REPLAY_GAIN_MODE = preferencesKey<Int>("replay_gain_mode")
    val REPLAY_GAIN_PREAMP = preferencesKey<Float>("replay_gain_preamp")
    val REPLAY_GAIN_DEFAULT_PREAMP = preferencesKey<Float>("replay_gain_default_preamp")
    val ENABLE_FRAME_SKIP = preferencesKey<Boolean>("enable_frame_skip")
    val SKIP_LOOP_FILTER = preferencesKey<Int>("skip_loop_filter")
    val ALLOW_TIME_STRETCH_AUDIO = preferencesKey<Boolean>("allow_time_stretch_audio")
    val ENABLE_DIGITAL_AUDIO_OUTPUT = preferencesKey<Boolean>("enable_digital_audio_output")
    val HARDWARE_ACCELERATION = preferencesKey<Int>("hardware_acceleration")
  }

  override val debugAndLogging: Boolean
    get() = BuildConfig.DEBUG && BuildConfig.VLC_LOGGING

  override fun enableVerboseMode(): Boolean = state.value[ENABLE_VERBOSE_MODE, false]
  override suspend fun enableVerboseMode(verbose: Boolean): Boolean =
    dataStore.set(ENABLE_VERBOSE_MODE, verbose)

  override fun chroma(): Chroma = state.value[VIDEO_CHROMA, Chroma.DEFAULT]
  override suspend fun chroma(chroma: Chroma): Boolean = dataStore.set(VIDEO_CHROMA, chroma)

  override fun networkCachingAmount(): Millis =
    state.value[NETWORK_CACHING_MILLISECONDS, DEFAULT_NETWORK_CACHING].toMillis()

  override suspend fun networkCachingAmount(millis: Millis): Boolean =
    dataStore.set(NETWORK_CACHING_MILLISECONDS, millis.coerceIn(NETWORK_CACHING_RANGE).value)

  override fun subtitleEncoding() = state.value[SUBTITLE_ENCODING, SubtitleEncoding.DEFAULT]
  override suspend fun subtitleEncoding(encoding: SubtitleEncoding): Boolean =
    dataStore.set(SUBTITLE_ENCODING, encoding)

  override fun replayGainMode() = state.value[REPLAY_GAIN_MODE, ReplayGainMode.DEFAULT]
  override suspend fun replayGainMode(mode: ReplayGainMode) =
    dataStore.set(REPLAY_GAIN_MODE, mode)

  override fun replayGainPreamp(): Amp = state.value[REPLAY_GAIN_PREAMP, 0.0F].toAmp()
  override suspend fun replayGainPreamp(preamp: Amp): Boolean =
    dataStore.set(REPLAY_GAIN_PREAMP, preamp.value)

  override fun replayGainDefaultPreamp(): Amp = state.value[REPLAY_GAIN_DEFAULT_PREAMP, 0F].toAmp()
  override suspend fun replayGainDefaultPreamp(preamp: Amp): Boolean =
    dataStore.set(REPLAY_GAIN_DEFAULT_PREAMP, preamp.value)

  override fun enableFrameSkip(): Boolean = state.value[ENABLE_FRAME_SKIP, false]
  override suspend fun enableFrameSkip(enable: Boolean): Boolean =
    dataStore.set(ENABLE_FRAME_SKIP, enable)

  override fun skipLoopFilter() = state.value[SKIP_LOOP_FILTER, SkipLoopFilter.DEFAULT]
  override suspend fun skipLoopFilter(filter: SkipLoopFilter): Boolean =
    dataStore.set(SKIP_LOOP_FILTER, filter)

  override fun allowTimeStretchAudio(): Boolean = state.value[ALLOW_TIME_STRETCH_AUDIO, true]
  override suspend fun allowTimeStretchAudio(allow: Boolean): Boolean =
    dataStore.set(ALLOW_TIME_STRETCH_AUDIO, allow)

  override fun digitalAudioOutputEnabled(): Boolean =
    state.value[ENABLE_DIGITAL_AUDIO_OUTPUT, false]

  override suspend fun digitalAudioOutputEnabled(enabled: Boolean): Boolean =
    dataStore.set(ENABLE_DIGITAL_AUDIO_OUTPUT, enabled)

  override fun hardwareAcceleration(): HardwareAcceleration =
    state.value[HARDWARE_ACCELERATION, HardwareAcceleration.AUTOMATIC]

  override suspend fun hardwareAcceleration(acceleration: HardwareAcceleration): Boolean =
    dataStore.set(HARDWARE_ACCELERATION, acceleration)
}
