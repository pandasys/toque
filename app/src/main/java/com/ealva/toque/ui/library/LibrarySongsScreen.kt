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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioDescrResult
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.NamedSongListType
import com.ealva.toque.db.SongListType
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.ealva.toque.ui.audio.LocalAudioQueueModel.PlayResult
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
    with(serviceBinder) { add(AllSongsViewModel(get(), lookup())) }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllSongsViewModel>()
    val songs = viewModel.allSongs.collectAsState()
    val selected = viewModel.selectedItems.asState()

    var promptResult by remember {
      mutableStateOf<PlayResult>(PlayResult.Executed)
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      LibraryItemsActions(
        songs.value.size,
        selected.value.inSelectionMode,
        selected.value.selectedCount,
        play = { promptResult = viewModel.play() },
        shuffle = { promptResult = viewModel.shuffle() },
        playNext = { viewModel.playNext() },
        addToUpNext = { viewModel.addToUpNext() },
        addToPlaylist = { },
        selectAllOrNone = { all -> if (all) viewModel.selectAll() else viewModel.clearSelection() },
        startSearch = {}
      )
      SongItemList(
        list = songs.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.mediaClicked(it) },
        itemLongClicked = { viewModel.mediaLongClicked(it) }
      )
      if (promptResult.needPrompt) {
        val prompt = promptResult as PlayResult.PromptRequired
        val itemCount = prompt.itemCount
        fun dismiss() { promptResult = PlayResult.Executed }
        fun execute(clearQueue: ClearQueue) {
          prompt.execute(clearQueue)
          dismiss()
        }
        PlayUpNextPrompt(
          itemCount = itemCount,
          onDismiss = ::dismiss,
          onClear = { execute(ClearQueue(true)) },
          onDoNotClear = { execute(ClearQueue(true)) },
          onCancel = ::dismiss,
        )
      }
    }
  }
}

private class AllSongsViewModel(
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueModel,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, dispatcher), ScopedServices.Activated {
  override val namedSongListType: NamedSongListType =
    NamedSongListType(fetch(R.string.All), SongListType.All)

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): AudioDescrResult = audioMediaDao.getAllAudio(filter)
}

@Composable
fun PlayUpNextPrompt(
  itemCount: Int,
  onDismiss: () -> Unit,
  onClear: () -> Unit,
  onDoNotClear: () -> Unit,
  onCancel: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    text = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.UpNextActionPrompt,
          itemCount,
          itemCount,
        )
      )
    },
    buttons = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(all = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        Button(
          modifier = Modifier,
          onClick = onClear
        ) {
          Text("Clear")
        }
        Button(
          modifier = Modifier,
          onClick = onDoNotClear
        ) {
          Text("Add")
        }
        Button(
          modifier = Modifier,
          onClick = onCancel
        ) {
          Text("Cancel")
        }
      }
    }
  )
}
