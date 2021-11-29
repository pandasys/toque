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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.NamedSongListType
import com.ealva.toque.db.SongListType
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.github.michaelbull.result.Result
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
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

@Immutable
@Parcelize
data class LibrarySongsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(AllSongsViewModel(key, get(), lookup())) }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllSongsViewModel>()
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      CategoryTitleBar(viewModel.categoryItem)
      LibraryItemsActions(
        itemCount = songs.value.size,
        inSelectionMode = selected.value.inSelectionMode,
        selectedCount = selected.value.selectedCount,
        play = { viewModel.play() },
        shuffle = { viewModel.shuffle() },
        playNext = { viewModel.playNext() },
        addToUpNext = { viewModel.addToUpNext() },
        addToPlaylist = { viewModel.addToPlaylist() },
        selectAllOrNone = { all -> if (all) viewModel.selectAll() else viewModel.clearSelection() },
        startSearch = {}
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

private class AllSongsViewModel(
  private val key: ComposeKey,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueModel,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, dispatcher), ScopedServices.Activated {
  private val categories = LibraryCategories()

  val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val namedSongListType: NamedSongListType =
    NamedSongListType(fetch(R.string.All), SongListType.All)

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, DaoMessage> =
    audioMediaDao.getAllAudio(filter)
}
