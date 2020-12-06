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
import com.ealva.toque.prefs.AppPreferencesSingleton
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
    val appPrefs = prefsSingleton.instance()
    appPrefs.allowDuplicates(true)
    expect(appPrefs.allowDuplicates()).toBe(true)
    appPrefs.allowDuplicates(false)
    expect(appPrefs.allowDuplicates()).toBe(false)
  }

  @Test
  fun testGoToNowPlaying() = coroutineRule.runBlockingTest {
    val appPrefs = prefsSingleton.instance()
    appPrefs.goToNowPlaying(false)
    expect(appPrefs.goToNowPlaying()).toBe(false)
    appPrefs.goToNowPlaying(true)
    expect(appPrefs.goToNowPlaying()).toBe(true)
  }
}
