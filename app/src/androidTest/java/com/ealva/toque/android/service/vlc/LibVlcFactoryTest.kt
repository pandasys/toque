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
import com.ealva.toque.service.vlc.LibVlc
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.LibVlcSingleton
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
class LibVlcFactoryTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context
  private lateinit var testFile: File
  private lateinit var dataStoreScope: TestCoroutineScope
  private lateinit var singleton: LibVlcPrefsSingleton

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    testFile = tempFolder.newFile("dummy.preferences_pb")
    dataStoreScope = TestCoroutineScope(coroutineRule.testDispatcher + Job())
    singleton = LibVlcPrefsSingleton(LibVlcPrefs.Companion::make, testFile, dataStoreScope)
  }

  @After
  fun cleanup() {
    dataStoreScope.cleanupTestCoroutines()
  }

  @Test
  fun testGetInstance() = coroutineRule.runBlockingTest {
    val factory = LibVlcSingleton(
      appCtx,
      singleton,
      dispatcher = coroutineRule.testDispatcher
    )

    // Test will break when upgrading LibVlc and that's expected (obviously)
    factory.withInstance { libVlc ->
      expect(libVlc.libVlcVersion()).toBe("3.0.16 Vetinari")
    }
  }
}
