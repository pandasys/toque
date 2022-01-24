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

@file:Suppress("UnstableApiUsage")

package com.ealva.toque.common

import com.google.common.escape.UnicodeEscaper
import com.google.common.net.PercentEscaper

enum class Escapers(private val escaper: UnicodeEscaper) {
  /**
   * "?:\"*|/\\<>" are reserved, so that's our minimum. We may treat others as reserved. We won't
   * allow "%" as safe character so our filename is reversible. We also allow space because this is
   * for a filename, not path. The ranges 0..9, a..z and A..Z are always safe. Remember that when a
   * file name is in a file scheme Uri, the space character will appear as %20, but will be a space
   * on the filesystem.
   */
  Filename(PercentEscaper("-._~!$'(),;&=@ ", false)),

  /**
   * Very conservative escaper. Only "-_.*" are considered safe, so everything other than those
   * characters, 0..9, a..z and A..Z will be escaped. Currently this is passed to the Google search
   * engine looking for artwork, but could be used for any Url.
   */
  WebSearch(PercentEscaper("-_.*", false));

  fun escape(string: String): String = escaper.escape(string)
}

fun String.escapeForFile(): String = Escapers.Filename.escape(this)
fun String.escapeForWeb(): String = Escapers.WebSearch.escape(this)
