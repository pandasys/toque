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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

@Immutable
@Parcelize
data class AlbumSongsForArtistScreen(
  private val albumId: AlbumId,
  private val artistId: ArtistId
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(AlbumSongsForArtistViewModel(albumId, artistId, get(), lookup(), backstack))
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AlbumSongsForArtistViewModel>()
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      SongsItemsActions(
        itemCount = songs.value.size,
        selectedItems = selected.value,
        viewModel = viewModel
      )
      SongItemList(
        list = songs.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.mediaClicked(it.id) },
        itemLongClicked = { viewModel.mediaLongClicked(it.id) }
      )
    }
  }
}

private class AlbumSongsForArtistViewModel(
  private val albumId: AlbumId,
  private val artistId: ArtistId,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueModel,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, backstack, dispatcher),
  ScopedServices.Activated {
  override val categoryToken: CategoryToken get() = CategoryToken(albumId)

  override suspend fun getAudioList(audioMediaDao: AudioMediaDao, filter: Filter) =
    audioMediaDao.getAlbumAudio(id = albumId, restrictTo = artistId, filter = filter)
}
