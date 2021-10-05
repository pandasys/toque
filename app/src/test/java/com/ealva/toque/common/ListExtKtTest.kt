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

package com.ealva.toque.common

import com.nhaarman.expect.expect
import org.junit.Test

class ListExtKtTest {
  @Test
  fun `test window of empty list`() {
    val list = emptyList<Int>()
    expect(list.windowOf15(-1)).toBeEmpty()
  }

  @Test
  fun `test window of smaller than window list`() {
    listOf(1).also {
      expect(it.windowOf15(0)).toBe(it)
    }

    List(2) { it }.also {
      expect(it.windowOf15(0)).toBe(it)
    }

    List(15) { it }.also {
      expect(it.windowOf15(0)).toBe(it)
    }
  }

  @Test
  fun `test window list`() {
    List(16) { it }.also { list ->
      expect(list.windowOf15(0)).toBe(List(15) { it })
      expect(list.windowOf15(2)).toBe(List(15) { it })
      expect(list.windowOf15(7)).toBe(List(15) { it })
      expect(list.windowOf15(8)).toBe(List(15) { it + 1 })
      expect(list.windowOf15(15)).toBe(List(15) { it + 1 })
    }

    List(1000) { it }.also { list ->
      expect(list.windowOf15(0)).toBe(List(15) { it })
      expect(list.windowOf15(2)).toBe(List(15) { it })
      expect(list.windowOf15(7)).toBe(List(15) { it })
      expect(list.windowOf15(8)).toBe(List(15) { it + 1 })
      expect(list.windowOf15(500)).toBe(List(15) { it + 493 })
      expect(list.windowOf15(999)).toBe(List(15) { it + 985 })
      expect(list.windowOf15(992)).toBe(List(15) { it + 985 })
      expect(list.windowOf15(991)).toBe(List(15) { it + 984 })
    }
  }
}
