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

package com.ealva.toque.service.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.Amp
import com.ealva.toque.sharedtest.parcelize
import com.nhaarman.expect.expect
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BandDataTest {

  @Test
  fun testParcelizePresetData() {
    val data = EqPreset.BandData(
      Amp(-5.5F),
      listOf(
        Amp(3),
        Amp(5),
        Amp(8),
        Amp(3),
        Amp(0),
        Amp(-3),
        Amp(-5),
        Amp(-10),
        Amp(-18),
        Amp(-20),
      )
    )
    data.parcelize { presetData -> expect(presetData).toBe(data) }
  }
}
