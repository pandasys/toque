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
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_DUCK_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_DUCK_VOLUME
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_END_OF_QUEUE_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_FADE_ON_PLAY_PAUSE
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_LAST_SCAN_TIME
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_PAUSE_FADE
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_PLAY_UP_NEXT_ACTION
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_SCROBBLER
import com.ealva.toque.prefs.AppPreferences.Companion.DEFAULT_SELECT_MEDIA_ACTION
import com.ealva.toque.prefs.AppPreferencesSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.prefs.SelectMediaAction
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.test.shared.expect
import com.ealva.toque.test.shared.toNotBe
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
      expect(allowDuplicates()).toBe(DEFAULT_ALLOW_DUPLICATES)
      allowDuplicates(true)
      expect(allowDuplicates()).toBe(true)
      allowDuplicates(false)
      expect(allowDuplicates()).toBe(false)

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testGoToNowPlaying() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(goToNowPlaying()).toBe(DEFAULT_GO_TO_NOW_PLAYING)
      goToNowPlaying(false)
      expect(goToNowPlaying()).toBe(false)
      goToNowPlaying(true)
      expect(goToNowPlaying()).toBe(true)

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testIgnoreSmallFiles() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(ignoreSmallFiles()).toBe(DEFAULT_IGNORE_SMALL_FILES)
      expect(ignoreSmallFiles(true)).toBe(true)
      expect(ignoreSmallFiles()).toBe(true)

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testIgnoreThreshold() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(ignoreThreshold()).toBe(DEFAULT_IGNORE_THRESHOLD)
      ignoreThreshold(5000.toMillis())
      expect(ignoreThreshold()).toBe(5000.toMillis())

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testLastScanTime() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(lastScanTime()).toBe(DEFAULT_LAST_SCAN_TIME)
      lastScanTime(Millis.ONE_MINUTE)
      expect(lastScanTime()).toBe(Millis.ONE_MINUTE)
      lastScanTime(DEFAULT_LAST_SCAN_TIME)
      expectAllAreDefault()
    }
  }

  @Test
  fun testFadeOnPlayPause() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(fadeOnPlayPause()).toBe(DEFAULT_FADE_ON_PLAY_PAUSE)
      expect(fadeOnPlayPause(true)).toBe(true)
      expect(fadeOnPlayPause()).toBe(true)

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testPlayPauseFade() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(playPauseFadeLength()).toBe(DEFAULT_PLAY_PAUSE_FADE)
      playPauseFadeLength(1000.toMillis())
      expect(playPauseFadeLength()).toBe(1000.toMillis())
      playPauseFadeLength(2000.toMillis())
      expect(playPauseFadeLength()).toBe(2000.toMillis())
      playPauseFadeLength(100.toMillis())
      expect(playPauseFadeLength()).toBe(AppPreferences.PLAY_PAUSE_FADE_RANGE.start)
      playPauseFadeLength(5000.toMillis())
      expect(playPauseFadeLength()).toBe(AppPreferences.PLAY_PAUSE_FADE_RANGE.endInclusive)

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testScrobblerPackage() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(scrobbler()).toBe(DEFAULT_SCROBBLER)
      ScrobblerPackage.values().forEach { pkg ->
        expect(scrobbler(pkg)).toBe(true)
        expect(scrobbler()).toBe(pkg)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testDuckAction() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(duckAction()).toBe(DEFAULT_DUCK_ACTION)
      DuckAction.values().forEach { action ->
        expect(duckAction(action)).toBe(true)
        expect(duckAction()).toBe(action)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testDuckVolume() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(duckVolume()).toBe(DEFAULT_DUCK_VOLUME)
      expect(duckVolume((-100).toVolume())).toBe(true)
      expect(duckVolume()).toBe(Volume.ZERO)
      expect(duckVolume(1000.toVolume())).toBe(true)
      expect(duckVolume()).toBe(Volume.ONE_HUNDRED)
      arrayOf(10.toVolume(), 30.toVolume(), 67.toVolume(), 91.toVolume()).forEach { vol ->
        expect(duckVolume(vol)).toBe(true)
        expect(duckVolume()).toBe(vol)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testPlayUpNextAction() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(playUpNextAction()).toBe(DEFAULT_PLAY_UP_NEXT_ACTION)
      PlayUpNextAction.values().forEach { action ->
        expect(playUpNextAction(action)).toBe(true)
        expect(playUpNextAction()).toBe(action)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testEndOfQueueAction() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(endOfQueueAction()).toBe(DEFAULT_END_OF_QUEUE_ACTION)
      EndOfQueueAction.values().forEach { action ->
        expect(endOfQueueAction(action)).toBe(true)
        expect(endOfQueueAction()).toBe(action)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  @Test
  fun testSelectMediaAction() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(selectMediaAction()).toBe(DEFAULT_SELECT_MEDIA_ACTION)
      SelectMediaAction.values().forEach { action ->
        expect(selectMediaAction(action)).toBe(true)
        expect(selectMediaAction()).toBe(action)
      }

      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  /**
   * Ensure expected key count same as actual to ensure tests match actual
   */
  @Test
  fun testExpectedKeyCount() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      expect(asMap().keys).toHaveSize(13)
    }
  }

  @Test
  fun testResetAllToDefault() = coroutineRule.runBlockingTest {
    with(prefsSingleton.instance()) {
      resetAllToDefault()
      expectAllAreDefault()
      expect(allowDuplicates(!DEFAULT_ALLOW_DUPLICATES)).toBe(true)
      expect(goToNowPlaying(!DEFAULT_GO_TO_NOW_PLAYING)).toBe(true)
      expect(ignoreSmallFiles(!DEFAULT_IGNORE_SMALL_FILES)).toBe(true)
      expect(ignoreThreshold(DEFAULT_IGNORE_THRESHOLD + 1000L)).toBe(true)
      expect(fadeOnPlayPause(!DEFAULT_FADE_ON_PLAY_PAUSE)).toBe(true)
      expect(playPauseFadeLength(DEFAULT_PLAY_PAUSE_FADE + 500L)).toBe(true)
      expect(scrobbler(ScrobblerPackage.LastFm)).toBe(true)
      expect(duckAction(DuckAction.Pause)).toBe(true)
      expect(duckVolume(DEFAULT_DUCK_VOLUME + 1)).toBe(true)
      expect(playUpNextAction(PlayUpNextAction.ClearUpNext)).toBe(true)
      expect(endOfQueueAction(EndOfQueueAction.Stop)).toBe(true)
      expect(selectMediaAction(SelectMediaAction.AddToUpNext)).toBe(true)
      expectAllAreNotDefault()
      resetAllToDefault()
      expectAllAreDefault()
    }
  }

  private fun AppPreferences.expectAllAreDefault() {
    expect(allowDuplicates()).toBe(DEFAULT_ALLOW_DUPLICATES)
    expect(goToNowPlaying()).toBe(DEFAULT_GO_TO_NOW_PLAYING)
    expect(ignoreSmallFiles()).toBe(DEFAULT_IGNORE_SMALL_FILES)
    expect(ignoreThreshold()).toBe(DEFAULT_IGNORE_THRESHOLD)
    expect(fadeOnPlayPause()).toBe(DEFAULT_FADE_ON_PLAY_PAUSE)
    expect(playPauseFadeLength()).toBe(DEFAULT_PLAY_PAUSE_FADE)
    expect(scrobbler()).toBe(DEFAULT_SCROBBLER)
    expect(duckAction()).toBe(DEFAULT_DUCK_ACTION)
    expect(duckVolume()).toBe(DEFAULT_DUCK_VOLUME)
    expect(playUpNextAction()).toBe(DEFAULT_PLAY_UP_NEXT_ACTION)
    expect(selectMediaAction()).toBe(DEFAULT_SELECT_MEDIA_ACTION)
  }

  private fun AppPreferences.expectAllAreNotDefault() {
    expect(allowDuplicates()).toNotBe(DEFAULT_ALLOW_DUPLICATES)
    expect(goToNowPlaying()).toNotBe(DEFAULT_GO_TO_NOW_PLAYING)
    expect(ignoreSmallFiles()).toNotBe(DEFAULT_IGNORE_SMALL_FILES)
    expect(ignoreThreshold()).toNotBe(DEFAULT_IGNORE_THRESHOLD)
    expect(fadeOnPlayPause()).toNotBe(DEFAULT_FADE_ON_PLAY_PAUSE)
    expect(playPauseFadeLength()).toNotBe(DEFAULT_PLAY_PAUSE_FADE)
    expect(scrobbler()).toNotBe(DEFAULT_SCROBBLER)
    expect(duckAction()).toNotBe(DEFAULT_DUCK_ACTION)
    expect(duckVolume()).toNotBe(DEFAULT_DUCK_VOLUME)
    expect(playUpNextAction()).toNotBe(DEFAULT_PLAY_UP_NEXT_ACTION)
    expect(selectMediaAction()).toNotBe(DEFAULT_SELECT_MEDIA_ACTION)
  }
}
