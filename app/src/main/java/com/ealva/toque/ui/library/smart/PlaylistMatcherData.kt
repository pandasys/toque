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

package com.ealva.toque.ui.library.smart

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.reify
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class PlaylistMatcherData(
  val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType
) : Parcelable {
  override fun toString(): String = name.value
}

val PlaylistMatcherData.asMatcherData: MatcherData
  get() = MatcherData(text = name.value, first = type.id.toLong(), second = id.value)

val MatcherData.asPlaylistMatcherData: PlaylistMatcherData
  get() = PlaylistMatcherData(
    playlistId,
    playlistName,
    playlistType  ?: PlayListType.System
  )

val MatcherData.playlistName: PlaylistName
  get() = text.asPlaylistName

val MatcherData.playlistType: PlayListType?
  get() = PlayListType::class.reify(first.toInt())

val MatcherData.playlistId: PlaylistId
  get() = PlaylistId(second)
