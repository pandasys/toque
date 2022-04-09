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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoResult
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.asAlbumIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.library.AlbumsViewModel.AlbumInfo
import com.ealva.toque.ui.nav.goToScreen
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
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
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(AllAlbumsViewModel(key, get(), get(), backstack, lookup())) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllAlbumsViewModel>()
    val scrollConnection = remember { CategoryHeaderScrollConnection() }
    val albums = viewModel.albumFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        categoryItem = viewModel.categoryItem,
        itemCount = albums.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() },
        scrollConnection = scrollConnection,
        selectActions = {
          AlbumSelectActions(
            selectedCount = selected.value.selectedCount,
            buttonColors = ActionButtonDefaults.colors(),
            selectAlbumArt = { viewModel.selectAlbumArt() }
          )
        }
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
  localAudioQueueModel: LocalAudioQueueViewModel
) : BaseAlbumsViewModel(albumDao, backstack, localAudioQueueModel) {
  private val categories = LibraryCategories()

  val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): DaoResult<List<AlbumDescription>> = albumDao.getAllAlbums(filter)

  override fun goToAlbumSongs(album: AlbumInfo) = backstack.goToScreen(
    AlbumSongsScreen(album.id, album.title, album.artwork, album.artist, fetch(R.string.Albums))
  )

  override suspend fun makeCategoryMediaList(
    albumList: List<AlbumInfo>
  ): Result<CategoryMediaList, Throwable> = audioMediaDao
    .getMediaForAlbums(
      albumList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asAlbumIdList
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
    .map { idList -> CategoryMediaList(idList, CategoryToken(albumList.last().id)) }
}

@Composable
private fun AlbumSelectActions(
  selectedCount: Int,
  buttonColors: ButtonColors,
  selectAlbumArt: () -> Unit
) {
  Row(
    modifier = Modifier
  ) {
    ActionButton(
      modifier = Modifier.height(24.dp),
      iconSize = 24.dp,
      drawable = R.drawable.ic_image,
      description = R.string.MediaInfo,
      enabled = selectedCount == 1,
      colors = buttonColors,
      onClick = selectAlbumArt
    )
  }
}
