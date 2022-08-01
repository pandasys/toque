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
import com.ealva.toque.db.DaoResult
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.library.data.AlbumInfo
import com.ealva.toque.ui.library.data.makeCategoryMediaList
import com.ealva.toque.ui.nav.goToScreen
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Immutable
@Parcelize
data class AlbumsScreen(
  private val noArg: String = ""
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) = with(serviceBinder) {
    add(
      AllAlbumsViewModel(
        category = LibraryCategories.Albums,
        albumDao = get(),
        audioMediaDao = get(),
        appPrefs = get(AppPrefs.QUALIFIER),
        backstack = backstack,
        localAudioQueueModel = lookup()
      )
    )
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllAlbumsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val albums = viewModel.albumFlow.collectAsState()
    val selected = viewModel.selectedItems.collectAsState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        category = viewModel.category,
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

interface AllAlbumsViewModel : AlbumsViewModel {

  val category: LibraryCategories.LibraryCategory

  companion object {
    operator fun invoke(
      category: LibraryCategories.LibraryCategory,
      albumDao: AlbumDao,
      audioMediaDao: AudioMediaDao,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      localAudioQueueModel: LocalAudioQueueViewModel
    ): AllAlbumsViewModel = AllAlbumsViewModelImpl(
      category, albumDao, audioMediaDao, appPrefs, backstack, localAudioQueueModel
    )
  }
}

private class AllAlbumsViewModelImpl(
  override val category: LibraryCategories.LibraryCategory,
  albumDao: AlbumDao,
  private val audioMediaDao: AudioMediaDao,
  appPrefs: AppPrefsSingleton,
  backstack: Backstack,
  localAudioQueueModel: LocalAudioQueueViewModel
) : BaseAlbumsViewModel(albumDao, backstack, localAudioQueueModel, appPrefs), AllAlbumsViewModel {
  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): DaoResult<List<AlbumDescription>> = albumDao.getAllAlbums(filter)

  override fun goToAlbumSongs(album: AlbumInfo) = backstack.goToScreen(
    AlbumSongsScreen(
      album.id,
      album.title,
      album.artwork,
      album.artist,
      formatSongsInfo(album.songCount, album.duration, album.year),
      fetch(R.string.Albums)
    )
  )

  override suspend fun List<AlbumInfo>.makeCategoryMediaList() =
    makeCategoryMediaList(audioMediaDao)
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
