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

import com.ealva.toque.persist.HasConstId

enum class SubtitleEncoding(override val id: Int, private val value: String) : HasConstId {
  Default(1, ""),
  System(2, "system"),
  UTF8(3, "UTF-8"),
  UTF16(4, "UTF-16"),
  UTF16BE(5, "UTF-16BE"),
  UTF16LE(6, "UTF-16LE"),
  GB18030(7, "GB18030"),
  ISO885915(8, "ISO-8859-15"),
  Windows1252(9, "Windows-1252"),
  IBM850(10, "IBM850"),
  ISO88592(11, "ISO-8859-2"),
  Windows1250(12, "Windows-1250"),
  ISO88593(13, "ISO-8859-3"),
  ISO885910(14, "ISO-8859-10"),
  Windows1251(15, "Windows-1251"),
  KOI8R(16, "KOI8-R"),
  KOI8U(17, "KOI8-U"),
  ISO88596(18, "ISO-8859-6"),
  Windows1256(19, "Windows-1256"),
  ISO88597(20, "ISO-8859-7"),
  Windows1253(21, "Windows-1253"),
  ISO88598(22, "ISO-8859-8"),
  Windows1255(23, "Windows-1255"),
  ISO88599(24, "ISO-8859-9"),
  Windows1254(25, "Windows-1254"),
  ISO885911(26, "ISO-8859-11"),
  Windows874(27, "Windows-874"),
  ISO885913(28, "ISO-8859-13"),
  Windows1257(29, "Windows-1257"),
  ISO885914(30, "ISO-8859-14"),
  ISO885916(31, "ISO-8859-16"),
  ISO2022CNEXT(32, "ISO-2022-CN-EXT"),
  EUCCN(33, "EUC-CN"),
  ISO2022JP2(34, "ISO-2022-JP-2"),
  EUCJP(35, "EUC-JP"),
  Shift_JIS(36, "Shift_JIS"),
  CP949(37, "CP949"),
  ISO2022KR(38, "ISO-2022-KR"),
  Big5(39, "Big5"),
  ISO2022TW(40, "ISO-2022-TW"),
  Big5HKSCS(41, "Big5-HKSCS"),
  VISCII(42, "VISCII"),
  Windows1258(43, "Windows-1258");

  override fun toString(): String = value

  companion object {
    val DEFAULT = Default
  }
}
