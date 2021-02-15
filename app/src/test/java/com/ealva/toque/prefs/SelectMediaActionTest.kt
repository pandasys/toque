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

package com.ealva.toque.prefs

import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.test.prefs.SelectMediaActionProviderStub
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test

class SelectMediaActionTest {
  private lateinit var provider: SelectMediaActionProviderStub
  private var list: MediaIdList = MediaIdList(0)

  @Before
  fun setup() {
    provider = SelectMediaActionProviderStub()
  }

  @Test
  fun testPlayAction() {
    SelectMediaAction.Play.performAction(provider, list)
    expect(provider._playSongListCalled).toBeGreaterThan(0)
    expect(provider._playSongList).toBe(list)
  }

  @Test
  fun testPlayNextAction() {
    SelectMediaAction.PlayNext.performAction(provider, list)
    expect(provider._playListNextCalled).toBeGreaterThan(0)
    expect(provider._playListNext).toBe(list)
  }

  @Test
  fun testAddToUpNext() {
    SelectMediaAction.AddToUpNext.performAction(provider, list)
    expect(provider._addListToUpNextCalled).toBeGreaterThan(0)
    expect(provider._addListToUpNext).toBe(list)
  }
}
