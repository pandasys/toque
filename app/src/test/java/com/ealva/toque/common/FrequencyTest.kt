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

package com.ealva.toque.common

import com.nhaarman.expect.expect
import org.junit.Test

class FrequencyTest {
  @Test
  fun testGetDisplayString() {
    val freqList = listOf(
      Frequency(31F) to "31 Hz",
      Frequency(63F) to "63 Hz",
      Frequency(125F) to "125 Hz",
      Frequency(250F) to "250 Hz",
      Frequency(500F) to "500 Hz",
      Frequency(1000F) to "1 kHz",
      Frequency(2000F) to "2 kHz",
      Frequency(4000F) to "4 kHz",
      Frequency(8000F) to "8 kHz",
      Frequency(16000F) to "16 kHz"
    )
    freqList.forEach { (frequency, result) ->
      expect(frequency.displayString).toBe(result)
    }
  }
}
