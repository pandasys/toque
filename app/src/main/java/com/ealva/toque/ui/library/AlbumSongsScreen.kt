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

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.library.data.SongInfo
import com.github.michaelbull.result.Result
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

@Immutable
@Parcelize
data class AlbumSongsScreen(
  private val albumId: AlbumId,
  private val title: AlbumTitle,
  private val artwork: Uri,
  private val artist: ArtistName,
  private val tertiaryInfo: String?,
  private val backTo: String
) : ComposeKey(), LibraryItemsScreen, ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(AlbumSongsViewModel(albumId, get(), lookup(), get(AppPrefs.QUALIFIER), backstack))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AlbumSongsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.collectAsState()
    val year = viewModel.albumYear.collectAsState()

    AlbumSongs(songs.value, scrollConnection, selected.value, year.value, viewModel)
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun AlbumSongs(
    songs: List<SongInfo>,
    scrollConnection: HeightResizeScrollConnection,
    selected: SelectedItems<MediaId>,
    year: Int,
    viewModel: AlbumSongsViewModel
  ) {
    // Coming from Now Playing we do not have count/duration/year info. We will calculate count,
    // and duration, and the model queries the year and we get via flow
    val tertiary = tertiaryInfo ?: songs.toSongCount(year)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      ScreenHeaderWithArtwork(artwork = artwork) {
        SongListHeaderInfo(
          title = title.value,
          subtitle = artist.value,
          tertiaryInfo = tertiary,
          itemCount = songs.size,
          selectedItems = selected,
          viewModel = viewModel,
          buttonColors = ActionButtonDefaults.overArtworkColors(),
          backTo = backTo,
          scrollConnection = scrollConnection,
          back = { viewModel.goBack() }
        )
      }
      SongItemList(
        list = songs,
        selectedItems = selected,
        itemClicked = { viewModel.mediaClicked(it.id) },
        itemLongClicked = { viewModel.mediaLongClicked(it.id) },
      )
    }
  }
}

interface AlbumSongsViewModel : SongsViewModel {
  val albumYear: StateFlow<Int>

  companion object {
    operator fun invoke(
      albumId: AlbumId,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      appPrefs: AppPrefsSingleton,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AlbumSongsViewModel = AlbumSongsViewModelImpl(
      albumId,
      audioMediaDao,
      localAudioQueueModel,
      appPrefs,
      backstack,
      dispatcher
    )
  }
}

private class AlbumSongsViewModelImpl(
  private val albumId: AlbumId,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  appPrefs: AppPrefsSingleton,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher = Dispatchers.Main
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, appPrefs, backstack, dispatcher),
  AlbumSongsViewModel, ScopedServices.Activated {

  override val albumYear = MutableStateFlow(0)

  override val categoryToken: CategoryToken
    get() = CategoryToken(albumId)

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> {
    albumYear.value = audioMediaDao.albumDao.getAlbumYear(albumId)
    return audioMediaDao.getAlbumAudio(id = albumId, filter = filter)
  }
}
