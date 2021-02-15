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

package com.ealva.toque.android.service.vlc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.toAmp
import com.ealva.toque.common.toMillis
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.vlc.Chroma
import com.ealva.toque.service.vlc.HardwareAcceleration
import com.ealva.toque.service.vlc.LibVlcPreferences
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ALLOW_TIME_STRETCH
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_CHROMA
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_DIGITAL_AUDIO
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_FRAME_SKIP
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_ENABLE_VERBOSE_MODE
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_HARDWARE_ACCELERATION
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_NETWORK_CACHING
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_REPLAY_GAIN_DEFAULT
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_REPLAY_GAIN_MODE
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_REPLAY_PREAMP
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_SKIP_LOOP_FILTER
import com.ealva.toque.service.vlc.LibVlcPreferences.Companion.DEFAULT_SUBTITLE_ENCODING
import com.ealva.toque.service.vlc.LibVlcPreferencesSingleton
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.service.vlc.SubtitleEncoding
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class LibVlcPreferencesTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context
  private lateinit var prefsSingleton: LibVlcPreferencesSingleton

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    prefsSingleton = LibVlcPreferencesSingleton(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testEnableVerboseMode() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(enableVerboseMode()).toBe(false)
      expect(enableVerboseMode(true)).toBe(true)
      expect(enableVerboseMode()).toBe(true)

      resetAllToDefault()
    }
  }

  @Test
  fun testChroma() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(chroma()).toBe(Chroma.DEFAULT)
      Chroma.values().forEach { chroma ->
        expect(chroma(chroma)).toBe(true)
        expect(chroma()).toBe(chroma)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testNetworkCachingAmount() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(networkCachingAmount()).toBe(DEFAULT_NETWORK_CACHING)
      expect(networkCachingAmount(5000.toMillis())).toBe(true)
      expect(networkCachingAmount()).toBe(5000.toMillis())
      resetAllToDefault()
    }
  }

  @Test
  fun testSubtitleEncoding() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(subtitleEncoding()).toBe(SubtitleEncoding.DEFAULT)
      SubtitleEncoding.values().forEach { encoding ->
        expect(subtitleEncoding(encoding)).toBe(true)
        expect(subtitleEncoding()).toBe(encoding)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testReplayGainMode() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(replayGainMode()).toBe(ReplayGainMode.DEFAULT)
      ReplayGainMode.values().forEach { mode ->
        expect(replayGainMode(mode)).toBe(true)
        expect(replayGainMode()).toBe(mode)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testReplayPreamp() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(replayPreamp()).toBe(DEFAULT_REPLAY_PREAMP)
      expect(replayPreamp((-50).toAmp())).toBe(true)
      expect(replayPreamp()).toBe(EqPreset.AMP_RANGE.start)
      expect(replayPreamp(100.toAmp())).toBe(true)
      expect(replayPreamp()).toBe(EqPreset.AMP_RANGE.endInclusive)
      expect(replayPreamp(15.toAmp())).toBe(true)
      expect(replayPreamp()).toBe(15.toAmp())

      resetAllToDefault()
    }
  }

  @Test
  fun testDefaultReplayGain() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(defaultReplayGain()).toBe(DEFAULT_REPLAY_GAIN_DEFAULT)
      expect(defaultReplayGain(5.toAmp())).toBe(true)
      expect(defaultReplayGain()).toBe(5.toAmp())
      resetAllToDefault()
    }
  }

  @Test
  fun testEnableFrameSkip() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(enableFrameSkip()).toBe(DEFAULT_ENABLE_FRAME_SKIP)
      expect(enableFrameSkip(!DEFAULT_ENABLE_FRAME_SKIP)).toBe(true)
      expect(enableFrameSkip()).toBe(!DEFAULT_ENABLE_FRAME_SKIP)
      resetAllToDefault()
    }
  }

  @Test
  fun testSkipLoopFilter() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {

      resetAllToDefault()
    }
  }

  @Test
  fun testAllowTimeStretchAudio() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {

      resetAllToDefault()
    }
  }

  @Test
  fun testDigitalAudioOutputEnabled() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(digitalAudioOutputEnabled()).toBe(DEFAULT_ENABLE_DIGITAL_AUDIO)
      expect(digitalAudioOutputEnabled(!DEFAULT_ENABLE_DIGITAL_AUDIO)).toBe(true)
      expect(digitalAudioOutputEnabled()).toBe(!DEFAULT_ENABLE_DIGITAL_AUDIO)

      resetAllToDefault()
    }
  }

  @Test
  fun testHardwareAcceleration() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(hardwareAcceleration()).toBe(HardwareAcceleration.DEFAULT)
      HardwareAcceleration.values().forEach { accel ->
        expect(hardwareAcceleration(accel)).toBe(true)
        expect(hardwareAcceleration()).toBe(accel)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testResetAllToDefault() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  private fun LibVlcPreferences.expectAllAreDefault() {
    expect(enableVerboseMode()).toBe(DEFAULT_ENABLE_VERBOSE_MODE)
    expect(chroma()).toBe(DEFAULT_CHROMA)
    expect(networkCachingAmount()).toBe(DEFAULT_NETWORK_CACHING)
    expect(subtitleEncoding()).toBe(DEFAULT_SUBTITLE_ENCODING)
    expect(replayGainMode()).toBe(DEFAULT_REPLAY_GAIN_MODE)
    expect(replayPreamp()).toBe(DEFAULT_REPLAY_PREAMP)
    expect(defaultReplayGain()).toBe(DEFAULT_REPLAY_GAIN_DEFAULT)
    expect(enableFrameSkip()).toBe(DEFAULT_ENABLE_FRAME_SKIP)
    expect(skipLoopFilter()).toBe(DEFAULT_SKIP_LOOP_FILTER)
    expect(allowTimeStretchAudio()).toBe(DEFAULT_ALLOW_TIME_STRETCH)
    expect(digitalAudioOutputEnabled()).toBe(DEFAULT_ENABLE_DIGITAL_AUDIO)
    expect(hardwareAcceleration()).toBe(DEFAULT_HARDWARE_ACCELERATION)
  }
}
