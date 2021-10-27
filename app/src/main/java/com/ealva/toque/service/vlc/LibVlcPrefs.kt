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
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.common.MillisRange
import com.ealva.toque.prefs.AmpStorePref
import com.ealva.toque.prefs.BaseToquePreferenceStore
import com.ealva.toque.prefs.MillisStorePref
import com.ealva.toque.service.vlc.LibVlcPrefs.Companion.DEFAULT_DEFAULT_REPLAY_GAIN
import com.ealva.toque.service.vlc.LibVlcPrefs.Companion.DEFAULT_REPLAY_GAIN
import com.ealva.toque.service.vlc.LibVlcPrefs.Companion.NETWORK_CACHING_RANGE

typealias LibVlcPrefsSingleton = PreferenceStoreSingleton<LibVlcPrefs>

/**
 * Preferences which require that [LibVlcSingleton] be reset: [LibVlcSingleton.reset]
 * * network_caching [networkCachingAmount]
 * * opengl
 * * chroma_format [chroma]
 * * deblocking [skipLoopFilter]
 * * enable_frame_skip [enableFrameSkip]
 * * enable_time_stretching_audio [allowTimeStretchAudio]
 * * enable_verbose_mode [enableVerboseMode]
 * * prefer_smbv1 - currently not used
 * * aout [audioOutputModule]
 * * subtitles_size
 * * subtitles_bold
 * * subtitles_color
 * * subtitles_background
 * * subtitle_text_encoding [subtitleEncoding]
 * * casting_passthrough
 * * casting_quality
 */
interface LibVlcPrefs : PreferenceStore<LibVlcPrefs> {
  /**
   * If true verbose mode set to -vv else -v. Requires [LibVlcSingleton.reset] and media player
   * reset.
   */
  val enableVerboseMode: BoolPref
  /**
   * Sets audio output to AudioTrack or OpenSL ES. Requires [LibVlcSingleton.reset] and media player
   * reset.
   */
  val audioOutputModule: StorePref<Int, AudioOutputModule>
  /**
   * Sets --android-display-chroma. Requires [LibVlcSingleton.reset] and media player
   * reset.
   */
  val chroma: StorePref<Int, Chroma>
  /**
   * Sets --network-caching. Requires [LibVlcSingleton.reset] and media player reset.
   */
  val networkCachingAmount: MillisStorePref
  /** Sets --subsdec-encoding. Requires [LibVlcSingleton.reset] and media player reset. */
  val subtitleEncoding: StorePref<Int, SubtitleEncoding>
  /** Sets --audio-replay-gain-mode. Requires [LibVlcSingleton.reset] and media player reset. */
  val replayGainMode: StorePref<Int, ReplayGainMode>
  /** Sets --audio-replay-gain-preamp. Requires [LibVlcSingleton.reset] and media player reset. */
  val replayGainPreamp: AmpStorePref
  /** Sets --audio-replay-gain-default. Requires [LibVlcSingleton.reset] and media player reset. */
  val defaultReplayGain: AmpStorePref
  /**
   * If true sets --avcodec-skip-frame and --avcodec-skip-idct to "2" else "0". Requires
   * [LibVlcSingleton.reset] and active media player reset.
   */
  val enableFrameSkip: BoolPref
  /**
   * Sets --avcodec-skiploopfilter deblocking value. Requires [LibVlcSingleton.reset] and media
   * player reset.
   */
  val skipLoopFilter: StorePref<Int, SkipLoopFilter>
  /**
   * If true sets --audio-time-stretch else sets --no-audio-time-stretch Requires
   * [LibVlcSingleton.reset] and media player reset.
   */
  val allowTimeStretchAudio: BoolPref

  val digitalAudioOutputEnabled: BoolPref
  /** Sets hardware/software decoding per media. Requires media player reset */
  val hardwareAcceleration: StorePref<Int, HardwareAcceleration>

  companion object {
    val NETWORK_CACHING_RANGE: MillisRange = Millis(0)..Millis.ONE_MINUTE
    const val SUB_AUTODETECT_PATHS = "./Subtitles, ./subtitles, ./Subs, ./subs"
    val DEFAULT_REPLAY_GAIN = Amp.NONE.coerceIn(Amp.REPLAY_GAIN_RANGE)
    val DEFAULT_DEFAULT_REPLAY_GAIN = Amp(-6).coerceIn(Amp.REPLAY_GAIN_RANGE)

    fun make(storage: Storage): LibVlcPrefs = LibVlcPrefsImpl(storage)
  }
}

private class LibVlcPrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<LibVlcPrefs>(storage), LibVlcPrefs {
  override val enableVerboseMode by preference(false)
  override val audioOutputModule by enumPref(AudioOutputModule.DEFAULT)
  override val chroma by enumPref(Chroma.DEFAULT)
  override val networkCachingAmount by millisPref(Millis.ONE_SECOND) {
    it.coerceIn(NETWORK_CACHING_RANGE)
  }
  override val subtitleEncoding by enumPref(SubtitleEncoding.DEFAULT)
  override val replayGainMode by enumPref(ReplayGainMode.DEFAULT)
  override val replayGainPreamp by ampPref(DEFAULT_REPLAY_GAIN) {
    it.coerceIn(Amp.REPLAY_GAIN_RANGE)
  }
  override val defaultReplayGain by ampPref(DEFAULT_DEFAULT_REPLAY_GAIN) {
    it.coerceIn(Amp.REPLAY_GAIN_RANGE)
  }
  override val enableFrameSkip by preference(false)
  override val skipLoopFilter by enumPref(SkipLoopFilter.DEFAULT)
  override val allowTimeStretchAudio by preference(true)
  override val digitalAudioOutputEnabled by preference(false)
  override val hardwareAcceleration by enumPref(HardwareAcceleration.DEFAULT)
}
