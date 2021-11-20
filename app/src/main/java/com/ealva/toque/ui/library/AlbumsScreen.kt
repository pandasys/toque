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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.persist.AlbumId
import com.github.michaelbull.result.Result
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Immutable
@Parcelize
data class AlbumsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllAlbumsViewModel(get(), backstack)) }
  }
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllAlbumsViewModel>()
    val albums = viewModel.albumList.collectAsState()
    val selectedAlbums = viewModel.selectedItems.asState()
    AlbumsList(
      list = albums.value,
      selectedItems = selectedAlbums.value,
      itemClicked = { viewModel.itemClicked(it) },
      itemLongClicked = { viewModel.itemLongClicked(it) }
    )
  }
}

private class AllAlbumsViewModel(
  albumDao: AlbumDao,
  backstack: Backstack
) : BaseAlbumsViewModel(albumDao, backstack) {
  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): Result<List<AlbumDescription>, DaoMessage> = albumDao.getAllAlbums(filter)

  override fun goToAlbumSongs(albumId: AlbumId) {
    val title = albumList.value
      .find { it.id == albumId }
      ?.title ?: AlbumTitle("")
    backstack.goTo(AlbumSongsScreen(albumId, title))
  }
}
