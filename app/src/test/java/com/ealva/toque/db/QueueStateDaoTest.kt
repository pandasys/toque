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

package com.ealva.toque.db

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.toque.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ealva.toque.test.db.CommonQueueStateDaoTest as Common

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class QueueStateDaoTest {
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
  fun `test get before init`() = coroutineRule.runBlockingTest {
    Common.testGetBeforePersist(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test persist state`() = coroutineRule.runBlockingTest {
    Common.testPersistState(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test factory`() = coroutineRule.runBlockingTest {
    Common.testFactory(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test close dao and remake`() = coroutineRule.runBlockingTest {
    Common.testCloseDaoAndRemake(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test closed dao throws`() = coroutineRule.runBlockingTest {
    thrown.expect(IllegalStateException::class.java)
    Common.testClosedDaoThrows(appCtx, coroutineRule.testDispatcher)
  }
}
