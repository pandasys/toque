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

package com.ealva.toque.ui.library

import android.net.Uri
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.persist.AlbumId
import kotlinx.coroutines.flow.StateFlow

interface AlbumsViewModel {
  data class AlbumInfo(
    val id: AlbumId,
    val title: AlbumTitle,
    val artwork: Uri,
    val artist: ArtistName,
    val songCount: Int
  )

  val albumList: StateFlow<List<AlbumInfo>>
  val selectedItems: SelectedItemsFlow<AlbumId>
  val searchFlow: StateFlow<String>

  fun setSearch(search: String)
  fun itemClicked(albumId: AlbumId)
  fun itemLongClicked(albumId: AlbumId)
}
