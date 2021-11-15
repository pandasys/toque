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
import android.os.Parcel
import android.os.Parcelable

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
}

class ParcelWrap<T>(val value: T)

val <T> T.parcel: ParcelWrap<T> get() = ParcelWrap(this)

inline fun <reified T: Parcelable> ParcelWrap<T>.test(
  flags: Int = 0,
  classLoader: ClassLoader = T::class.java.classLoader!!,
  checks: (T) -> Unit
): T {
  // Create the parcel
  val parcel: Parcel = Parcel.obtain()
  parcel.writeParcelable(this.value, flags)

  // Record dataPosition
  val eop = parcel.dataPosition()

  // Reset the parcel
  parcel.setDataPosition(0)

  // Read from the parcel
  val newObject = parcel.readParcelable<T>(classLoader)

  // Perform the checks provided in the lambda
  checks(newObject!!)

  // Verify dataPosition
  expect(eop).toBe(parcel.dataPosition()) {
    "writeToParcel wrote too much data or read didn't finish reading"
  }
  return newObject
}
