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

package com.ealva.toque.ui.library

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.ealva.toque.common.asHourMinutesSeconds
import kotlin.time.Duration

@Composable
fun CountDurationYear(songCount: Int, duration: Duration, year: Int) {
  Text(
    formatSongsInfo(songCount, duration, year),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
  )
}

private const val TREBLE_CLEF = "\uD834\uDD1E  "

fun formatSongsInfo(count: Int, duration: Duration, year: Int = 0): String = buildString {
  append(TREBLE_CLEF)
  append(count)
  append(" | ")
  append(duration.asHourMinutesSeconds)
  if (year > 0) {
    append(" | ")
    append(year)
  }
}
