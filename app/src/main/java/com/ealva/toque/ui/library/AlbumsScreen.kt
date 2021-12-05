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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoEmptyResult
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.asAlbumIdList
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Immutable
@Parcelize
data class AlbumsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(AllAlbumsViewModel(key, get(), get(), backstack, lookup())) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllAlbumsViewModel>()
    val albums = viewModel.albumFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      CategoryTitleBar(viewModel.categoryItem)
      LibraryItemsActions(
        itemCount = albums.value.size,
        selectedItems = selected.value,
        viewModel = viewModel
      )
      AlbumsList(
        list = albums.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) }
      )
    }
  }
}

private class AllAlbumsViewModel(
  private val key: ComposeKey,
  albumDao: AlbumDao,
  private val audioMediaDao: AudioMediaDao,
  backstack: Backstack,
  localAudioQueueModel: LocalAudioQueueModel
) : BaseAlbumsViewModel(albumDao, backstack, localAudioQueueModel) {
  private val categories = LibraryCategories()

  val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): Result<List<AlbumDescription>, DaoMessage> = albumDao.getAllAlbums(filter)

  override fun goToAlbumSongs(albumId: AlbumId) = backstack.goTo(AlbumSongsScreen(albumId))

  override suspend fun makeCategoryMediaList(
    albumList: List<AlbumsViewModel.AlbumInfo>
  ) = audioMediaDao
    .getMediaForAlbums(
      albumList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asAlbumIdList
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { DaoEmptyResult }
    .map { idList -> CategoryMediaList(idList, CategoryToken(albumList.last().id)) }
}
