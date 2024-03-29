/*
 * Copyright 2021 Eric A. Snell
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import com.ealva.toque.test.db.CommonQueueStateDaoTest as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class QueuePositionStateDaoTest {
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
  fun testGetBeforeInit() = runTest {
    Common.testGetBeforePersist(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testPersistState() = runTest {
    Common.testPersistState(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testFactory() = runTest {
    Common.testFactory(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testCloseDaoAndRemake() = runTest {
    Common.testCloseDaoAndRemake(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testClosedDaoThrows() = runTest {
    thrown.expect(IllegalStateException::class.java)
    Common.testClosedDaoThrows(appCtx, coroutineRule.testDispatcher)
  }
}
