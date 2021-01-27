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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.common.toMillis
import com.ealva.toque.common.toVolume
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.prefs.AppPreferencesSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.ScrobblerPackage
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
class AppPreferencesTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context
  private lateinit var prefsSingleton: AppPreferencesSingleton

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    prefsSingleton = AppPreferencesSingleton(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testAllowDuplicates() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(allowDuplicates()).toBe(false)
      allowDuplicates(true)
      expect(allowDuplicates()).toBe(true)
      allowDuplicates(false)
      expect(allowDuplicates()).toBe(false)

      resetAllToDefault()
    }
  }

  @Test
  fun testGoToNowPlaying() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(goToNowPlaying()).toBe(true)
      goToNowPlaying(false)
      expect(goToNowPlaying()).toBe(false)
      goToNowPlaying(true)
      expect(goToNowPlaying()).toBe(true)

      resetAllToDefault()
    }
  }

  @Test
  fun testIgnoreSmallFiles() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(ignoreSmallFiles()).toBe(false)
      expect(ignoreSmallFiles(true)).toBe(true)
      expect(ignoreSmallFiles()).toBe(true)

      resetAllToDefault()
    }
  }

  @Test
  fun testIgnoreThreshold() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(ignoreThreshold()).toBe(Millis.ZERO)
      ignoreThreshold(5000.toMillis())
      expect(ignoreThreshold()).toBe(5000.toMillis())

      resetAllToDefault()
    }
  }

  @Test
  fun testLastScanTime() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(lastScanTime()).toBe(Millis.ZERO)
      lastScanTime(Millis.ONE_MINUTE)
      expect(lastScanTime()).toBe(Millis.ONE_MINUTE)
      lastScanTime(Millis.ZERO)
      resetAllToDefault()
    }
  }

  @Test
  fun testFadeOnPlayPause() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(fadeOnPlayPause()).toBe(false)
      expect(fadeOnPlayPause(true)).toBe(true)
      expect(fadeOnPlayPause()).toBe(true)

      resetAllToDefault()
    }
  }

  @Test
  fun testPlayPauseFade() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(playPauseFadeLength()).toBe(AppPreferences.DEFAULT_PLAY_PAUSE_FADE)
      playPauseFadeLength(1000.toMillis())
      expect(playPauseFadeLength()).toBe(1000.toMillis())
      playPauseFadeLength(2000.toMillis())
      expect(playPauseFadeLength()).toBe(2000.toMillis())
      playPauseFadeLength(100.toMillis())
      expect(playPauseFadeLength()).toBe(AppPreferences.PLAY_PAUSE_FADE_RANGE.start)
      playPauseFadeLength(5000.toMillis())
      expect(playPauseFadeLength()).toBe(AppPreferences.PLAY_PAUSE_FADE_RANGE.endInclusive)

      resetAllToDefault()
    }
  }

  @Test
  fun testScrobblerPackage() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(scrobbler()).toBe(ScrobblerPackage.DEFAULT)
      ScrobblerPackage.values().forEach { pkg ->
        expect(scrobbler(pkg)).toBe(true)
        expect(scrobbler()).toBe(pkg)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testDuckAction() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(duckAction()).toBe(DuckAction.DEFAULT)
      DuckAction.values().forEach { action ->
        expect(duckAction(action)).toBe(true)
        expect(duckAction()).toBe(action)
      }

      resetAllToDefault()
    }
  }

  @Test
  fun testDuckVolume() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(duckVolume()).toBe(50.toVolume())
      expect(duckVolume((-100).toVolume())).toBe(true)
      expect(duckVolume()).toBe(Volume.ZERO)
      expect(duckVolume(1000.toVolume())).toBe(true)
      expect(duckVolume()).toBe(Volume.ONE_HUNDRED)
      arrayOf(10.toVolume(), 30.toVolume(), 67.toVolume(), 91.toVolume()).forEach { vol ->
        expect(duckVolume(vol)).toBe(true)
        expect(duckVolume()).toBe(vol)
      }

      resetAllToDefault()
    }
  }
}
