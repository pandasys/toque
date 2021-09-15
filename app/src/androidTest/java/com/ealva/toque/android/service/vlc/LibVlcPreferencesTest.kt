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
import com.ealva.prefstore.store.invoke
import com.ealva.toque.common.Amp
import com.ealva.toque.common.Millis
import com.ealva.toque.service.vlc.Chroma
import com.ealva.toque.service.vlc.HardwareAcceleration
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.ReplayGainMode
import com.ealva.toque.service.vlc.SubtitleEncoding
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class LibVlcPreferencesTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  private lateinit var appCtx: Context
  private lateinit var testFile: File
  private lateinit var dataStoreScope: TestCoroutineScope
  private lateinit var prefsSingleton: LibVlcPrefsSingleton

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    testFile = tempFolder.newFile("dummy.preferences_pb")
    dataStoreScope = TestCoroutineScope(coroutineRule.testDispatcher + Job())
    prefsSingleton = LibVlcPrefsSingleton(LibVlcPrefs.Companion::make, testFile, dataStoreScope)
  }

  @After
  fun cleanup() {
    dataStoreScope.cleanupTestCoroutines()
  }

  @Test
  fun testEnableVerboseMode() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(enableVerboseMode()).toBe(false)
      enableVerboseMode.set(true)
      expect(enableVerboseMode()).toBe(true)

      clear()
    }
  }

  @Test
  fun testChroma() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(chroma()).toBe(Chroma.DEFAULT)
      Chroma.values().forEach { chroma ->
        this.chroma.set(chroma)
        expect(chroma()).toBe(chroma)
      }

      clear()
    }
  }

  @Test
  fun testNetworkCachingAmount() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(networkCachingAmount()).toBe(networkCachingAmount.default)
      networkCachingAmount.set(Millis(5000))
      expect(networkCachingAmount()).toBe(Millis(5000))
      clear()
    }
  }

  @Test
  fun testSubtitleEncoding() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(subtitleEncoding()).toBe(SubtitleEncoding.DEFAULT)
      SubtitleEncoding.values().forEach { encoding ->
        subtitleEncoding.set(encoding)
        expect(subtitleEncoding()).toBe(encoding)
      }

      clear()
    }
  }

  @Test
  fun testReplayGainMode() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(replayGainMode()).toBe(ReplayGainMode.DEFAULT)
      ReplayGainMode.values().forEach { mode ->
        replayGainMode.set(mode)
        expect(replayGainMode()).toBe(mode)
      }

      clear()
    }
  }

  @Test
  fun testReplayPreamp() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(replayPreamp()).toBe(replayPreamp.default)
      replayPreamp.set(Amp(-50))
      expect(replayPreamp()).toBe(Amp.RANGE.start)
      replayPreamp.set(Amp(100))
      expect(replayPreamp()).toBe(Amp.RANGE.endInclusive)
      replayPreamp.set(Amp(15))
      expect(replayPreamp()).toBe(Amp(15))

      clear()
    }
  }

  @Test
  fun testDefaultReplayGain() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(defaultReplayGain()).toBe(defaultReplayGain.default)
      defaultReplayGain.set(Amp(5))
      expect(defaultReplayGain()).toBe(Amp(5))
      clear()
    }
  }

  @Test
  fun testEnableFrameSkip() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(enableFrameSkip()).toBe(enableFrameSkip.default)
      enableFrameSkip.set(!enableFrameSkip.default)
      expect(enableFrameSkip()).toBe(!enableFrameSkip.default)
      clear()
    }
  }

  @Test
  fun testSkipLoopFilter() = coroutineRule.runBlockingTest {
    prefsSingleton {

      clear()
    }
  }

  @Test
  fun testAllowTimeStretchAudio() = coroutineRule.runBlockingTest {
    prefsSingleton {

      clear()
    }
  }

  @Test
  fun testDigitalAudioOutputEnabled() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(digitalAudioOutputEnabled()).toBe(digitalAudioOutputEnabled.default)
      digitalAudioOutputEnabled.set(!digitalAudioOutputEnabled.default)
      expect(digitalAudioOutputEnabled()).toBe(!digitalAudioOutputEnabled.default)

      clear()
    }
  }

  @Test
  fun testHardwareAcceleration() = coroutineRule.runBlockingTest {
    prefsSingleton {
      expect(hardwareAcceleration()).toBe(HardwareAcceleration.DEFAULT)
      HardwareAcceleration.values().forEach { accel ->
        hardwareAcceleration.set(accel)
        expect(hardwareAcceleration()).toBe(accel)
      }

      clear()
    }
  }

  @Test
  fun testResetAllToDefault() = coroutineRule.runBlockingTest {
    prefsSingleton {
      clear()
      expectAllAreDefault()
    }
  }

  private fun LibVlcPrefs.expectAllAreDefault() {
    expect(enableVerboseMode()).toBe(enableVerboseMode.default)
    expect(chroma()).toBe(chroma.default)
    expect(networkCachingAmount()).toBe(networkCachingAmount.default)
    expect(subtitleEncoding()).toBe(subtitleEncoding.default)
    expect(replayGainMode()).toBe(replayGainMode.default)
    expect(replayPreamp()).toBe(replayPreamp.default)
    expect(defaultReplayGain()).toBe(defaultReplayGain.default)
    expect(enableFrameSkip()).toBe(enableFrameSkip.default)
    expect(skipLoopFilter()).toBe(skipLoopFilter.default)
    expect(allowTimeStretchAudio()).toBe(allowTimeStretchAudio.default)
    expect(digitalAudioOutputEnabled()).toBe(digitalAudioOutputEnabled.default)
    expect(hardwareAcceleration()).toBe(hardwareAcceleration.default)
  }
}
