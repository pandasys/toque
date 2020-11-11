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
import androidx.datastore.DataStore
import androidx.datastore.preferences.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.prefs.defaultValue
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import java.io.IOException

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testAllowDuplicates() = coroutineRule.runBlockingTest {
    val prefs = AppPreferences(appCtx, coroutineRule.testDispatcher)
    expect(AppPreferences.Keys.ALLOW_DUPLICATES.defaultValue).toBe(false)
    prefs.allowDuplicates(true)
    expect(prefs.allowDuplicates()).toBe(true)
    prefs.allowDuplicates(false)
    expect(prefs.allowDuplicates()).toBe(false)
  }

  @Test
  fun testSetAllowDuplicatesThrowsIOException() = coroutineRule.runBlockingTest {
    val dataStore = TestDataStore()
    val prefs = AppPreferences(dataStore)
    expect(prefs.allowDuplicates(true)).toBe(false)
  }

  @Test
  fun testGetAllowDuplicatesThrowsIOException() = coroutineRule.runBlockingTest {
    val dataStore = TestDataStore()
    val prefs = AppPreferences(dataStore)
    expect(prefs.allowDuplicates()).toBe(AppPreferences.Keys.ALLOW_DUPLICATES.defaultValue)
  }
}

class TestDataStore : DataStore<Preferences> {
  override val data: Flow<Preferences>
    get() = throw IOException()

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
    throw IOException()
  }
}
