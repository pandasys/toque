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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AudioDescrResult
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.persist.ComposerId
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

@Immutable
@Parcelize
data class ComposerSongsScreen(
  private val composerId: ComposerId
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(ComposerSongsViewModel(composerId, get())) }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ComposerSongsViewModel>()
    val songs = viewModel.allSongs.collectAsState()
    val selected = viewModel.selectedItems.asState()
    SongItemList(
      list = songs.value,
      selectedItems = selected.value,
      itemClicked = { viewModel.mediaClicked(it) },
      itemLongClicked = { viewModel.mediaLongClicked(it) }
    )
  }
}

private class ComposerSongsViewModel(
  private val composerId: ComposerId,
  audioMediaDao: AudioMediaDao,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, dispatcher), ScopedServices.Activated {
  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): AudioDescrResult = audioMediaDao.getComposerAudio(composerId = composerId, filter = filter)
}
