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

package com.ealva.toque.db

import androidx.annotation.DrawableRes
import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId

/**
 * Playlist type, never change the [id] as it is persisted. [icon] is a drawable respresenting the
 * type, and [sortPosition] is how a playlist is sorted in a list of playlists
 */
enum class PlayListType(
  override val id: Int,
  @DrawableRes val icon: Int,
  val sortPosition: Int
) : HasConstId {
  Rules(
    id = 1,
    icon = R.drawable.ic_format_list_bulleted_type,
    sortPosition = 1
  ),
  UserCreated(
    id = 2,
    icon = R.drawable.ic_format_list_numbered,
    sortPosition = 2
  ),
  File(
    id = 3,
    icon = R.drawable.ic_format_list_bulleted,
    sortPosition = 3
  ),
  System(
    id = 4,
    icon = R.drawable.ic_view_list,
    sortPosition = 4
  )
}
