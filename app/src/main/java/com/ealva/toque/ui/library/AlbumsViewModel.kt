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

import com.ealva.toque.persist.AlbumId
import com.ealva.toque.ui.library.data.AlbumInfo
import kotlinx.coroutines.flow.StateFlow

interface AlbumsViewModel : ActionsViewModel {

  val albumFlow: StateFlow<List<AlbumInfo>>
  val selectedItems: SelectedItemsFlow<AlbumId>
  val searchFlow: StateFlow<String>

  fun setSearch(search: String)
  fun itemClicked(album: AlbumInfo)
  fun itemLongClicked(album: AlbumInfo)

  fun selectAlbumArt()
  fun goBack()
}
