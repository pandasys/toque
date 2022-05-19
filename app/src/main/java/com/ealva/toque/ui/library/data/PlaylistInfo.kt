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

package com.ealva.toque.ui.library.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.PlayListType
import com.ealva.toque.persist.HasPersistentId
import com.ealva.toque.persist.PlaylistId
import kotlin.time.Duration

@Immutable
data class PlaylistInfo(
  override val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType,
  val songCount: Int,
  val duration: Duration,
  val artwork: Uri
) : HasPersistentId<PlaylistId>
