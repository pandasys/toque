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

package com.ealva.toque.common

import android.os.Parcelable
import com.ealva.ealvabrainz.common.ComposerName
import kotlinx.parcelize.Parcelize
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e

private val LOG by lazyLogger(PlaylistName::class)

@Parcelize
@JvmInline
value class PlaylistName(val value: String) : Parcelable {
  fun equalsIgnoreCase(other: PlaylistName): Boolean = this.value.equals(other.value, true)

  companion object {
    val UNKNOWN: PlaylistName = PlaylistName("Unknown")
  }
}

inline val String?.asPlaylistName: PlaylistName get() =
  PlaylistName(this?.trim() ?: "")

inline val PlaylistName.isValid: Boolean get() = value.isNotBlank()

fun PlaylistName.isValidAndUnique(list: Sequence<PlaylistName>): Boolean =
  isValid && list.none { element -> element.equalsIgnoreCase(this) }
