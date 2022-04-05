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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.common.preferredArt
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.PlayListType
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.PlaylistId
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
import kotlin.time.Duration

@Immutable
@Parcelize
data class PlaylistSongsScreen(
  private val playlistId: PlaylistId,
  private val playListType: PlayListType,
  private val playlistName: PlaylistName,
  private val artwork: Uri,
  private val backTo: String
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(PlaylistSongsViewModel(playlistId, playListType, get(), lookup(), backstack))
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<PlaylistSongsViewModel>()
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
    ) {
      ScreenHeaderWithArtwork(artwork = artwork) {
        SongListHeaderInfo(
          title = playlistName.value,
          subtitle = null,
          itemCount = songs.value.size,
          selectedItems = selected.value,
          viewModel = viewModel,
          buttonColors = ActionButtonDefaults.overArtworkColors(),
          backTo = backTo,
          back = { viewModel.goBack() }
        )
      }
      SongItemList(
        list = songs.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.mediaClicked(it.id) },
        itemLongClicked = { viewModel.mediaLongClicked(it.id) },
      )
    }
  }
}

interface PlaylistSongsViewModel : SongsViewModel {

  @Immutable
  interface PlaylistSongInfo : SongsViewModel.SongInfo {
    val position: Int

    companion object {
      operator fun invoke(
        position: Int,
        id: MediaId,
        title: Title,
        duration: Duration,
        rating: Rating,
        album: AlbumTitle,
        artist: ArtistName,
        artwork: Uri
      ): PlaylistSongInfo = PlaylistSongInfoData(
        position,
        id,
        title,
        duration,
        rating,
        album,
        artist,
        artwork
      )

      @Immutable
      data class PlaylistSongInfoData(
        override val position: Int,
        override val id: MediaId,
        override val title: Title,
        override val duration: Duration,
        override val rating: Rating,
        override val album: AlbumTitle,
        override val artist: ArtistName,
        override val artwork: Uri
      ) : PlaylistSongInfo
    }
  }

  companion object {
    operator fun invoke(
      playlistId: PlaylistId,
      playListType: PlayListType,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): PlaylistSongsViewModel = PlaylistSongsViewModelImpl(
      playlistId,
      playListType,
      audioMediaDao,
      localAudioQueueModel,
      backstack,
      dispatcher
    )
  }
}

private class PlaylistSongsViewModelImpl(
  private val playlistId: PlaylistId,
  private val playListType: PlayListType,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, backstack, dispatcher),
  PlaylistSongsViewModel {
  override val categoryToken: CategoryToken
    get() = CategoryToken(playlistId)

  override fun makeSongInfo(index: Int, audio: AudioDescription): SongsViewModel.SongInfo {
    return PlaylistSongsViewModel.PlaylistSongInfo(
      position = index,
      id = audio.mediaId,
      title = audio.title,
      duration = audio.duration,
      rating = audio.rating,
      album = audio.album,
      artist = audio.artist,
      artwork = audio.preferredArt
    )
  }

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> =
    audioMediaDao.getPlaylistAudio(playlistId, playListType, Filter.NoFilter, Limit.NoLimit)
}
