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

package com.ealva.toque.sharedtest

import android.os.Parcel
import android.os.Parcelable
import com.nhaarman.expect.expect

/**
 * Write to a parcel, read from the parcel, and then [verify] the results. [parcelableFlags] are
 * passed to [Parcel.writeParcelable]. The [loader] [ClassLoader] is obtained from the
 * receiver type [T]
 */
inline fun <reified T : Parcelable> T.parcelize(
  parcelableFlags: Int = 0,
  loader: ClassLoader = requireNotNull(T::class.java.classLoader) { "${T::class} loader null" },
  verify: (T) -> Unit
) : T {
  val parcel: Parcel = Parcel.obtain().apply { writeParcelable(this@parcelize, parcelableFlags) }
  val amountWritten = parcel.dataPosition()

  parcel.setDataPosition(0)

  return requireNotNull<T>(parcel.readParcelable(loader)) { "readParcelable null" }.also {
    expect(amountWritten).toBe(parcel.dataPosition()) { "Write and read amounts mismatch" }
    verify(it)
  }
}

/*
Example use where SelectedItems is a Parcelable created via the Parcelize annotation. The
contained InstanceId is below.

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.android.sharedtest.parcelize
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.ui.library.SelectedItems
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedItemsTest {

  @Test
  fun testParcelable() {
    val list = listOf(InstanceId(1), InstanceId(2), InstanceId(3))

    var before = SelectedItems<InstanceId>()
    list.forEach {
      before = before.toggleSelection(it)
    }
    before.parcelize { selection ->
      expect(selection).toBe(before) // compares equal
      expect(selection.hasSelection).toBe(true)
      list.forEach { expect(selection.isSelected(it)).toBe(true) }
    }
  }
}

@Parcelize
@JvmInline
value class InstanceId(val value: Long): Parcelable {
  /** True if [value] is >= 0, else false */
  inline val isValid: Boolean get() = value >= 0

  companion object {
    val INVALID = InstanceId(-1L)
  }
}
 */
