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
import androidx.compose.ui.res.stringResource
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.preferredArt
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

private val LOG by lazyLogger(ArtistSongsScreen::class)

@Immutable
@Parcelize
data class ArtistSongsScreen(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val artistName: ArtistName,
  private val artwork: Uri,
  private val backTo: String
) : BaseLibraryItemsScreen(), ScopeKey.Child, KoinComponent {

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        ArtistSongsViewModel(
          artistId = artistId,
          artistType = artistType,
          artwork = artwork,
          audioMediaDao = get(),
          localAudioQueueModel = lookup(),
          backstack = backstack
        )
      )
    }
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ArtistSongsViewModel>()
    val scrollConnection = remember { HeightResizeScrollConnection() }
    val songs = viewModel.songsFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()
    val artwork = viewModel.artistArt.collectAsState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
        .nestedScroll(scrollConnection)
    ) {
      ScreenHeaderWithArtwork(artwork = artwork.value) {
        SongListHeaderInfo(
          title = stringResource(id = artistType.allSongsRes),
          subtitle = null,
          itemCount = songs.value.size,
          selectedItems = selected.value,
          viewModel = viewModel,
          buttonColors = ActionButtonDefaults.overArtworkColors(),
          backTo = backTo,
          scrollConnection = scrollConnection,
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

interface ArtistSongsViewModel : SongsViewModel {
  val artistArt: StateFlow<Uri>

  companion object {
    operator fun invoke(
      artistId: ArtistId,
      artistType: ArtistType,
      artwork: Uri,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ArtistSongsViewModel = ArtistSongsViewModelImpl(
      artistId, artistType, artwork, audioMediaDao, localAudioQueueModel, backstack, dispatcher
    )
  }
}

private class ArtistSongsViewModelImpl(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  artwork: Uri,
  audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  backstack: Backstack,
  dispatcher: CoroutineDispatcher
) : BaseSongsViewModel(audioMediaDao, localAudioQueueModel, backstack, dispatcher),
  ArtistSongsViewModel {
  private val artistDao: ArtistDao = audioMediaDao.artistDao
  private val albumDao: AlbumDao = audioMediaDao.albumDao

  override val artistArt = MutableStateFlow(artwork)

  override fun onServiceRegistered() {
    super.onServiceRegistered()
    if (artistArt.value === Uri.EMPTY) {
      scope.launch {
        artistArt.value = artistDao.getArtwork(artistId)
          .onFailure { cause -> LOG.e(cause) { it("Error getting artwork for %s", artistId) } }
          .map { artwork -> artwork.preferredArt }
          .map { artwork ->
            if (artwork !== Uri.EMPTY) artwork else {
              albumDao.getAlbumArtFor(artistId, artistType)
                .onFailure { cause -> LOG.e(cause) { it("Error getting art for %s", artistId) } }
                .getOrElse { Uri.EMPTY }
            }
          }
          .getOrElse { Uri.EMPTY }
      }
    }
  }

  override val categoryToken: CategoryToken
    get() = CategoryToken(artistId)

  override suspend fun getAudioList(
    audioMediaDao: AudioMediaDao,
    filter: Filter
  ): Result<List<AudioDescription>, Throwable> = when (artistType) {
    ArtistType.SongArtist -> audioMediaDao.getArtistAudio(id = artistId, filter = filter)
    ArtistType.AlbumArtist -> audioMediaDao.getAlbumArtistAudio(id = artistId, filter = filter)
  }
}
