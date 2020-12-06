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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SubtitleEncodingTest {
  @Test
  fun `test members and id`() {
    val encodingValues = listOf(
      Pair("", SubtitleEncoding.Default),
      Pair("system", SubtitleEncoding.System),
      Pair("UTF-8", SubtitleEncoding.UTF8),
      Pair("UTF-16", SubtitleEncoding.UTF16),
      Pair("UTF-16BE", SubtitleEncoding.UTF16BE),
      Pair("UTF-16LE", SubtitleEncoding.UTF16LE),
      Pair("GB18030", SubtitleEncoding.GB18030),
      Pair("ISO-8859-15", SubtitleEncoding.ISO885915),
      Pair("Windows-1252", SubtitleEncoding.Windows1252),
      Pair("IBM850", SubtitleEncoding.IBM850),
      Pair("ISO-8859-2", SubtitleEncoding.ISO88592),
      Pair("Windows-1250", SubtitleEncoding.Windows1250),
      Pair("ISO-8859-3", SubtitleEncoding.ISO88593),
      Pair("ISO-8859-10", SubtitleEncoding.ISO885910),
      Pair("Windows-1251", SubtitleEncoding.Windows1251),
      Pair("KOI8-R", SubtitleEncoding.KOI8R),
      Pair("KOI8-U", SubtitleEncoding.KOI8U),
      Pair("ISO-8859-6", SubtitleEncoding.ISO88596),
      Pair("Windows-1256", SubtitleEncoding.Windows1256),
      Pair("ISO-8859-7", SubtitleEncoding.ISO88597),
      Pair("Windows-1253", SubtitleEncoding.Windows1253),
      Pair("ISO-8859-8", SubtitleEncoding.ISO88598),
      Pair("Windows-1255", SubtitleEncoding.Windows1255),
      Pair("ISO-8859-9", SubtitleEncoding.ISO88599),
      Pair("Windows-1254", SubtitleEncoding.Windows1254),
      Pair("ISO-8859-11", SubtitleEncoding.ISO885911),
      Pair("Windows-874", SubtitleEncoding.Windows874),
      Pair("ISO-8859-13", SubtitleEncoding.ISO885913),
      Pair("Windows-1257", SubtitleEncoding.Windows1257),
      Pair("ISO-8859-14", SubtitleEncoding.ISO885914),
      Pair("ISO-8859-16", SubtitleEncoding.ISO885916),
      Pair("ISO-2022-CN-EXT", SubtitleEncoding.ISO2022CNEXT),
      Pair("EUC-CN", SubtitleEncoding.EUCCN),
      Pair("ISO-2022-JP-2", SubtitleEncoding.ISO2022JP2),
      Pair("EUC-JP", SubtitleEncoding.EUCJP),
      Pair("Shift_JIS", SubtitleEncoding.Shift_JIS),
      Pair("CP949", SubtitleEncoding.CP949),
      Pair("ISO-2022-KR", SubtitleEncoding.ISO2022KR),
      Pair("Big5", SubtitleEncoding.Big5),
      Pair("ISO-2022-TW", SubtitleEncoding.ISO2022TW),
      Pair("Big5-HKSCS", SubtitleEncoding.Big5HKSCS),
      Pair("VISCII", SubtitleEncoding.VISCII),
      Pair("Windows-1258", SubtitleEncoding.Windows1258)
    )
    expect(SubtitleEncoding.values().size).toBe(encodingValues.size)
    expect(SubtitleEncoding.DEFAULT).toBe(SubtitleEncoding.Default)
    encodingValues.forEach { (value, enc) ->
      expect(enc.toString()).toBe(value)
    }
    SubtitleEncoding.values().forEachIndexed { index, chroma ->
      expect(chroma.id).toBe(index + 1)
    }
  }
}
