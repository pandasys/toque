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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.github.michaelbull.result.Result
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
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
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) = with(serviceBinder) {
    add(
      LibrarySongsViewModel(
        category = LibraryCategories.AllSongs,
        audioMediaDao = get(),
        localAudioQueueModel = lookup(),
        appPrefs = get(AppPrefs.QUALIFIER),
        backstack = backstack
      )
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<LibrarySongsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val songs = viewModel.songsFlow.collectAsState()
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
        itemCount = songs.value.size,
        selectedItems = selected.value,
        backTo = fetch(R.string.Library),
        scrollConnection = scrollConnection,
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
  val category: LibraryCategories.LibraryCategory,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  appPrefs: AppPrefsSingleton,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(
  audioMediaDao,
  localAudioQueueModel,
  appPrefs,
  backstack,
  dispatcher
) {
  override val categoryToken: CategoryToken = CategoryToken.All

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> = audioMediaDao.getAllAudio(filter)
}
