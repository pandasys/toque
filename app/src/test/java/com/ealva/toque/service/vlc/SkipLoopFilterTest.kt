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

package com.ealva.toque.service.vlc

import com.nhaarman.expect.expect
import org.junit.Test

class SkipLoopFilterTest {
  @Test
  fun `test members and id`() {
    val filterValues = setOf(
      Pair("-1", SkipLoopFilter.Auto),
      Pair("0", SkipLoopFilter.None),
      Pair("1", SkipLoopFilter.NonRef),
      Pair("2", SkipLoopFilter.Bidir),
      Pair("3", SkipLoopFilter.NonKey),
      Pair("4", SkipLoopFilter.All)
    )
    expect(SkipLoopFilter.values().size).toBe(filterValues.size)
    expect(SkipLoopFilter.DEFAULT).toBe(SkipLoopFilter.Auto)
    filterValues.forEach { (value, chroma) ->
      expect(chroma.toString()).toBe(value)
    }
    SkipLoopFilter.values().forEachIndexed { index, chroma ->
      expect(chroma.id).toBe(index + 1)
    }
  }
}
