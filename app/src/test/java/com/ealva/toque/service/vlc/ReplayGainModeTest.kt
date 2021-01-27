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

class ReplayGainModeTest {
  @Test
  fun `test members and id`() {
    val modeValues = setOf(
      Pair("none", ReplayGainMode.None),
      Pair("track", ReplayGainMode.Track),
      Pair("album", ReplayGainMode.Album)
    )
    expect(ReplayGainMode.values().size).toBe(modeValues.size)
    expect(ReplayGainMode.DEFAULT).toBe(ReplayGainMode.None)
    modeValues.forEach { (value, chroma) ->
      expect(chroma.toString()).toBe(value)
    }
    ReplayGainMode.values().forEachIndexed { index, mode ->
      expect(mode.id).toBe(index + 1)
    }
  }
}
