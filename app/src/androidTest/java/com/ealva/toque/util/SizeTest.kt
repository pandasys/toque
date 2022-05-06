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

package com.ealva.toque.util

import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.android.util.coerceIn
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SizeTest {
  @Test
  fun testCoerceInBoundary() {
    expect(Size(1000, 500).coerceIn(Size(500, 500))).toBe(Size(500, 250))
    expect(Size(750, 850).coerceIn(Size(500, 500))).toBe(Size(441, 500))
    expect(Size(850, 750).coerceIn(Size(500, 500))).toBe(Size(500, 441))
    expect(Size(1000, 500).coerceIn(Size(1000, 1000))).toBe(Size(1000, 500))
    expect(Size(500, 1000).coerceIn(Size(1000, 1000))).toBe(Size(500, 1000))
    expect(Size(1000, 500).coerceIn(Size(0, 0))).toBe(Size(0, 0))
    expect(Size(1000, 500).coerceIn(Size(1, 1))).toBe(Size(1, 0))
  }
}
