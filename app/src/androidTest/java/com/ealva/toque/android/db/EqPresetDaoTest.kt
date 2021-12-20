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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.ealva.toque.test.db.CommonPresetDaoTest as Common

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class EqPresetDaoTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testEstablishEqPresetTableMinimumRowid() = runTest {
    Common.testEstablishMinRowId(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUpdateMissingPreset() = runTest {
    Common.testUpdateMissingPreset(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testInsertPreset() = runTest {
    Common.testInsertPreset(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testInsertDuplicatePreset() = runTest {
    Common.testInsertDuplicatePreset(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUpdatePreset() = runTest {
    Common.testUpdatePreset(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUpdatePreAmp() = runTest {
    Common.testUpdatePreAmp(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testUpdateBAnd() = runTest {
    Common.testUpdateBand(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testDeletePreset() = runTest {
    Common.testDeletePreset(appCtx, coroutineRule.testDispatcher)
  }
}
