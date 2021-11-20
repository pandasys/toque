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

package com.ealva.toque.ui.library

import com.ealva.toque.persist.InstanceId
import com.ealva.toque.test.shared.toNotBe
import com.nhaarman.expect.expect
import org.junit.Test

class SelectedItemsTest {

  @Test
  fun testToggleAndHasSelection() {
    var selection: SelectedItems<InstanceId> = SelectedItems()
    expect(selection.hasSelection).toBe(false)

    selection = selection.toggleSelection(InstanceId(1))
    expect(selection.hasSelection).toBe(true)

    selection = selection.toggleSelection(InstanceId(1))
    expect(selection.hasSelection).toBe(false)

    selection = selection.toggleSelection(InstanceId(1))
    selection = selection.toggleSelection(InstanceId(2))
    selection = selection.toggleSelection(InstanceId(3))
    expect(selection.hasSelection).toBe(true)

    selection = selection.toggleSelection(InstanceId(1))
    expect(selection.hasSelection).toBe(true)

    selection = selection.toggleSelection(InstanceId(2))
    expect(selection.hasSelection).toBe(true)

    selection = selection.toggleSelection(InstanceId(3))
    expect(selection.hasSelection).toBe(false)
  }

  @Test
  fun testIsSelected() {
    var selection: SelectedItems<InstanceId> = SelectedItems()
    expect(selection.isSelected(InstanceId.INVALID)).toBe(false)

    selection = selection.toggleSelection(InstanceId(1))
    expect(selection.isSelected(InstanceId(1))).toBe(true)

    selection = selection.toggleSelection(InstanceId(1))
    expect(selection.isSelected(InstanceId(1))).toBe(false)

    listOf(InstanceId(1), InstanceId(2), InstanceId(3)).let { list ->
      list.forEach { id ->
        selection = selection.toggleSelection(id)
      }
      list.forEach { id ->
        expect(selection.isSelected(id)).toBe(true)
      }

      expect(list.size).toBeGreaterThan(2) // further tests expect min size list
      selection = selection.toggleSelection(list[1])
      expect(selection.isSelected(list[0])).toBe(true)
      expect(selection.isSelected(list[1])).toBe(false)
      expect(selection.isSelected(list[2])).toBe(true)
    }
  }

  @Test
  fun testEquals() {
    expect(SelectedItems<InstanceId>()).toBe(SelectedItems())
    var first = SelectedItems<InstanceId>().apply {
      toggleSelection(InstanceId(1))
    }
    var second = SelectedItems<InstanceId>().apply {
      toggleSelection(InstanceId(1))
    }
    expect(first).toBe(second)

    first = first.toggleSelection(InstanceId(2))
    second = second.toggleSelection(InstanceId(2))
    expect(first).toBe(second)

    first = first.toggleSelection(InstanceId(1))
    second = second.toggleSelection(InstanceId(1))
    expect(first).toBe(second)

    first = first.toggleSelection(InstanceId(1))
    expect(first).toNotBe(second)

    first = first.toggleSelection(InstanceId(1))
    expect(first).toBe(second)

    second = second.toggleSelection(InstanceId(3))
    expect(first).toNotBe(second)
  }

  @Test
  fun testClearSelection() {
    val list = listOf(InstanceId(1), InstanceId(2), InstanceId(3))

    var before = SelectedItems<InstanceId>()
    list.forEach {
      before = before.toggleSelection(it)
    }
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(true)
    list.forEach { expect(before.isSelected(it)).toBe(true) }

    before = before.toggleSelection(InstanceId(2))
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(true)
    expect(before.isSelected(InstanceId(1))).toBe(true)
    expect(before.isSelected(InstanceId(2))).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(true)

    before = before.clearSelection()
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(false)
    expect(before.isSelected(InstanceId(2))).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(false)
  }

  @Test
  fun testInSelectionMode() {
    val list = listOf(InstanceId(1), InstanceId(2), InstanceId(3))

    var before = SelectedItems<InstanceId>()
    list.forEach {
      before = before.toggleSelection(it)
    }
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(true)
    list.forEach { expect(before.isSelected(it)).toBe(true) }

    before = before.toggleSelection(InstanceId(2))
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(true)
    expect(before.isSelected(InstanceId(1))).toBe(true)
    expect(before.isSelected(InstanceId(2))).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(true)

    before = before.turnOffSelectionMode()
    expect(before.inSelectionMode).toBe(false)
    expect(before.hasSelection).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(false)
    expect(before.isSelected(InstanceId(2))).toBe(false)
    expect(before.isSelected(InstanceId(1))).toBe(false)

    before = SelectedItems()
    expect(before.inSelectionMode).toBe(false)
    before = before.turnOffSelectionMode()
    expect(before.inSelectionMode).toBe(false)
  }

  @Test
  fun testMakeWithSet() {
    val keySet = setOf(InstanceId(1), InstanceId(2), InstanceId(3))
    var before = SelectedItems(keySet)
    expect(before.inSelectionMode).toBe(true)
    expect(before.hasSelection).toBe(true)
    keySet.forEach { expect(before.isSelected(it)).toBe(true) }

    before = SelectedItems()
    expect(before.inSelectionMode).toBe(false)
    expect(before.hasSelection).toBe(false)
  }
}
