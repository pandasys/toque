/*
 * Copyright 2020 eAlva.com
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

class ChromaTest {
  @Test
  fun `test members and id`() {
    val chromaValues = setOf(
      Pair("I420", Chroma.I420),
      Pair("I411", Chroma.I411),
      Pair("I422", Chroma.I422),
      Pair("YUYV", Chroma.YUYV),
      Pair("UYVY", Chroma.UYVY),
      Pair("RV16", Chroma.RV16),
      Pair("RV24", Chroma.RV24),
      Pair("RV32", Chroma.RV32),
      Pair("I42N", Chroma.I42N),
      Pair("I41N", Chroma.I41N),
      Pair("GRAW", Chroma.GRAW)
    )
    expect(Chroma.values().size).toBe(chromaValues.size)
    expect(Chroma.DEFAULT).toBe(Chroma.RV16)
    chromaValues.forEach { (value, chroma) ->
      expect(chroma.toString()).toBe(value)
    }
    Chroma.values().forEachIndexed { index, chroma ->
      expect(chroma.id).toBe(index + 1)
    }
  }
}
