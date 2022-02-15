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
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.github.michaelbull.result.Result
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Suppress("unused")
private val LOG by lazyLogger(LibrarySongsScreen::class)

@Immutable
@Parcelize
data class LibrarySongsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    val key = this
    with(serviceBinder) { add(LibrarySongsViewModel(key, get(), lookup(), backstack)) }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<LibrarySongsViewModel>()
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
    ) {
      CategoryScreenHeader(
        viewModel = viewModel,
        categoryItem = viewModel.categoryItem,
        itemCount = songs.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        back = { viewModel.goBack() }
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

private class LibrarySongsViewModel(
  private val key: ComposeKey,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(
  audioMediaDao,
  localAudioQueueModel,
  backstack,
  dispatcher
) {
  private val categories = LibraryCategories()

  val categoryItem: LibraryCategories.CategoryItem
    get() = categories[key]

  override val categoryToken: CategoryToken = CategoryToken.All

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> = audioMediaDao.getAllAudio(filter)
}
