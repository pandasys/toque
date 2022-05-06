/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.sharedtest.parcelize
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedItemsAndroidTest {

  @Test
  fun testParcelable() {
    val list = listOf(InstanceId(1), InstanceId(2), InstanceId(3))

    var before = SelectedItems<InstanceId>()
    list.forEach {
      before = before.toggleSelection(it)
    }
    before.parcelize { selection ->
      expect(selection).toBe(before)
      expect(selection.inSelectionMode).toBe(true)
      expect(selection.hasSelection).toBe(true)
      list.forEach { expect(selection.isSelected(it)).toBe(true) }
    }

    before = before.toggleSelection(InstanceId(2))
    before.parcelize { selection ->
      expect(selection).toBe(before)
      expect(selection.inSelectionMode).toBe(true)
      expect(selection.hasSelection).toBe(true)
      expect(selection.isSelected(InstanceId(1))).toBe(true)
      expect(selection.isSelected(InstanceId(2))).toBe(false)
      expect(selection.isSelected(InstanceId(1))).toBe(true)
    }

    before = before.turnOffSelectionMode()
    before.parcelize { selection ->
      expect(selection).toBe(before)
      expect(selection.inSelectionMode).toBe(false)
      expect(selection.hasSelection).toBe(false)
      expect(selection.isSelected(InstanceId(1))).toBe(false)
      expect(selection.isSelected(InstanceId(2))).toBe(false)
      expect(selection.isSelected(InstanceId(1))).toBe(false)
    }
  }
}
