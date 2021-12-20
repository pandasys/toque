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

package com.ealva.toque.android.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import com.ealva.toque.test.db.CommonPresetAssocDaoTest as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class EqPresetAssociationDaoTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @BeforeTest
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testSetDefaultPreset() = runTest {
    Common.testSetAsDefault(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testReplaceDefault() = runTest {
    Common.testReplaceDefault(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testMakeAssociations() = runTest {
    Common.testMakeAssociations(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testReplaceAssociations() = runTest {
    Common.testReplaceAssociations(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testGetPreferredIdFallbackToDefault() = runTest {
    Common.testGetPreferredIdDefault(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testGetPreferredId() = runTest {
    Common.testGetPreferredId(appCtx, coroutineRule.testDispatcher)
  }
}
