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

package com.ealva.toque.service.scrobble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ealva.toque.prefs.AppPreferences
import com.ealva.toque.prefs.ScrobblerPackage
import com.ealva.toque.service.queue.QueueMediaItemFake
import com.ealva.toque.test.service.scrobbler.ScrobblerFactoryStub
import com.ealva.toque.test.service.scrobbler.ScrobblerStub
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ScrobblerFacadeTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context
  private lateinit var prefs: AppPreferences

  @Before
  fun init() {
    appCtx = ApplicationProvider.getApplicationContext()
    prefs = mockk()
  }

  @Test
  fun `test scrobble`() = coroutineRule.runBlockingTest {
    val item = QueueMediaItemFake()
    val scrobbler = ScrobblerStub()
    val factory = ScrobblerFactoryStub()
    factory._makeReturns = mutableListOf(scrobbler)
    factory._makeReturns
    every { prefs.scrobbler() } returns ScrobblerPackage.LastFm
    every { prefs.scrobblerFlow() } returns flow { emit(ScrobblerPackage.LastFm) }
    val facade = ScrobblerFacade(appCtx, prefs, factory, coroutineRule.testDispatcher)
    facade.start(item)
    facade.pause(item)
    facade.resume(item)
    facade.shutdown()

    expect(scrobbler._startCalled).toBe(1)
    expect(scrobbler._startItems).toHaveSize(1)
    expect(scrobbler._startItems[0]).toBe(item)

    expect(scrobbler._pauseCalled).toBe(1)
    expect(scrobbler._pauseItems).toHaveSize(1)
    expect(scrobbler._pauseItems[0]).toBe(item)

    expect(scrobbler._resumeCalled).toBe(1)
    expect(scrobbler._resumeItems).toHaveSize(1)
    expect(scrobbler._resumeItems[0]).toBe(item)

    expect(scrobbler._shutdownCalled).toBe(1)
  }

  @Test
  fun `test scrobbler pref change`() = coroutineRule.runBlockingTest {
    val item = QueueMediaItemFake()
    val scrobbler1 = ScrobblerStub()
    val scrobbler2 = ScrobblerStub()
    val factory = ScrobblerFactoryStub()
    factory._makeReturns = mutableListOf(scrobbler1, scrobbler2)
    every { prefs.scrobbler() } returns ScrobblerPackage.LastFm
    val flow = MutableStateFlow(ScrobblerPackage.LastFm)
    every { prefs.scrobblerFlow() } returns flow
    val facade = ScrobblerFacade(appCtx, prefs, factory, coroutineRule.testDispatcher)
    facade.start(item)

    expect(scrobbler1._startCalled).toBe(1)
    expect(scrobbler1._startItems).toHaveSize(1)
    expect(scrobbler1._startItems[0]).toBe(item)

    // This emit changes the scrobbler because it's a different package. If Start or Resume are
    // the previous action, that action is applied to the new scrobbler so it's aware of the
    // current "playing" state
    flow.value = ScrobblerPackage.SimpleLastFm
    expect(scrobbler1._shutdownCalled).toBe(1)
    expect(scrobbler2._startCalled).toBe(1)
    expect(scrobbler2._startItems).toHaveSize(1)
    expect(scrobbler2._startItems[0]).toBe(item)

    facade.pause(item)
    expect(scrobbler2._pauseCalled).toBe(1)
    expect(scrobbler2._pauseItems).toHaveSize(1)
    expect(scrobbler2._pauseItems[0]).toBe(item)
  }

  @Test
  fun `test set same scrobbler package does not flow duplicate`() = coroutineRule.runBlockingTest {
    val item = QueueMediaItemFake()
    val scrobbler1 = ScrobblerStub()
    val scrobbler2 = ScrobblerStub()
    val factory = ScrobblerFactoryStub()
    // Set factory to return 2 scrobbler instances, but should not be called to produce 2nd
    factory._makeReturns = mutableListOf(scrobbler1, scrobbler2)
    every { prefs.scrobbler() } returns ScrobblerPackage.LastFm
    val flow = MutableStateFlow(ScrobblerPackage.LastFm)
    every { prefs.scrobblerFlow() } returns flow
    val facade = ScrobblerFacade(appCtx, prefs, factory, coroutineRule.testDispatcher)
    facade.start(item)

    expect(scrobbler1._startCalled).toBe(1)
    expect(scrobbler1._startItems).toHaveSize(1)
    expect(scrobbler1._startItems[0]).toBe(item)

    // This emit should have no effect because it is equal to the previous scrobbler package
    flow.value = ScrobblerPackage.LastFm
    facade.pause(item)

    scrobbler2.verifyZeroInteractions()
    expect(scrobbler1._pauseCalled).toBe(1)
    expect(scrobbler1._pauseItems).toHaveSize(1)
    expect(scrobbler1._pauseItems[0]).toBe(item)
  }
}
