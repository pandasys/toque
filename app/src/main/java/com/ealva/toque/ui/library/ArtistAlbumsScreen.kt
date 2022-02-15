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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoResult
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.asAlbumIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.TextOvalBackground
import com.ealva.toque.ui.library.AlbumsViewModel.AlbumInfo
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.theme.toqueTypography
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Suppress("unused")
private val LOG by lazyLogger(ArtistAlbumsScreen::class)

@Parcelize
@Immutable
data class ArtistAlbumsScreen(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val artistName: ArtistName,
  private val artwork: Uri,
  private val songCount: Int,
  private val backTo: String
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        ArtistAlbumsViewModel(
          artistId = artistId,
          artistType = artistType,
          artistName = artistName,
          artwork = artwork,
          albumDao = get(),
          audioMediaDao = get(),
          localAudioQueueModel = lookup(),
          backstack = backstack
        )
      )
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ArtistAlbumsViewModel>()
    val albums = viewModel.albumFlow.collectAsState()
    val selected = viewModel.selectedItems.asState()
    val art = viewModel.artistArt.collectAsState()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding(bottom = false)
    ) {
      ScreenHeaderWithArtwork(artwork = art.value) {
        ArtistAlbumsHeaderInfo(
          artistName = artistName,
          albumCount = albums.value.size,
          selectedItems = selected.value,
          viewModel = viewModel,
          backTo = backTo,
          back = { viewModel.goBack() }
        )
      }
      AlbumsList(
        list = albums.value,
        selectedItems = selected.value,
        itemClicked = { albumInfo -> viewModel.itemClicked(albumInfo) },
        itemLongClicked = { albumInfo -> viewModel.itemLongClicked(albumInfo) },
        header = {
          AllArtistSongsHeader(
            artistType = artistType,
            songCount = songCount,
            doAllSongsSelected = { viewModel.goToArtistSongs() })
        }
      )
    }
  }
}

@Composable
private fun ArtistAlbumsHeaderInfo(
  artistName: ArtistName,
  albumCount: Int,
  selectedItems: SelectedItems<*>,
  viewModel: ActionsViewModel,
  backTo: String,
  back: () -> Unit
) {
  val buttonColors = ActionButtonDefaults.overArtworkColors()
  val inPortrait = LocalScreenConfig.current.inPortrait
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp)
  ) {
    val contentColor = buttonColors.contentColor(enabled = true).value
    val notInSelectionMode = !selectedItems.inSelectionMode
    if (notInSelectionMode) {
      Spacer(modifier = Modifier.height(2.dp))
      BackToButton(
        modifier = Modifier,
        backTo = backTo,
        back = back
      )
      Spacer(modifier = Modifier.height(if (inPortrait) 28.dp else 13.dp))
    }
    Spacer(
      modifier = Modifier
        .height(2.dp)
        .weight(1F)
    )
    TextOvalBackground(
      modifier = Modifier.padding(vertical = 2.dp),
      text = artistName.value,
      color = contentColor,
      style = toqueTypography.headerPrimary
    )
    LibraryItemsActions(
      itemCount = albumCount,
      selectedItems = selectedItems,
      viewModel = viewModel,
      buttonColors = ActionButtonDefaults.overArtworkColors(),
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AllArtistSongsHeader(
  artistType: ArtistType,
  songCount: Int,
  doAllSongsSelected: () -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = { doAllSongsSelected() }),
    icon = {
      Icon(
        painter = painterResource(id = artistType.typeIcon),
        contentDescription = stringResource(id = artistType.allSongsRes),
        modifier = Modifier.size(40.dp),
        tint = LocalContentColor.current
      )
    },
    text = {
      ListItemText(
        text = stringResource(id = artistType.allSongsRes),
        color = LocalContentColor.current
      )
    },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          songCount,
          songCount,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = LocalContentColor.current
      )
    },
  )
}

interface ArtistAlbumsViewModel : AlbumsViewModel {
  val artistArt: StateFlow<Uri>

  fun goToArtistSongs()

  companion object {
    operator fun invoke(
      artistId: ArtistId,
      artistType: ArtistType,
      artistName: ArtistName,
      artwork: Uri,
      albumDao: AlbumDao,
      audioMediaDao: AudioMediaDao,
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack
    ): ArtistAlbumsViewModel = ArtistAlbumsViewModelImpl(
      artistId,
      artistType,
      artistName,
      artwork,
      albumDao,
      audioMediaDao,
      localAudioQueueModel,
      backstack
    )
  }
}

private class ArtistAlbumsViewModelImpl(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val artistName: ArtistName,
  artwork: Uri,
  albumDao: AlbumDao,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  backstack: Backstack
) : BaseAlbumsViewModel(albumDao, backstack, localAudioQueueModel), ArtistAlbumsViewModel {
  override val artistArt = MutableStateFlow(artwork)

  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): DaoResult<List<AlbumDescription>> = albumDao.getAllAlbumsFor(artistId, artistType)

  override fun goToAlbumSongs(album: AlbumInfo) =
    backstack.goToScreen(
      AlbumSongsForArtistScreen(
        album.id,
        artistId,
        artistType,
        artistName,
        album.title,
        album.artwork,

        )
    )

  override suspend fun makeCategoryMediaList(
    albumList: List<AlbumInfo>
  ): Result<CategoryMediaList, Throwable> = audioMediaDao
    .getMediaForAlbums(
      albumList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asAlbumIdList,
      artistId
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { NoSuchElementException() }
    .map { idList -> CategoryMediaList(idList, CategoryToken(albumList.last().id)) }

  override fun goToArtistSongs() = backstack.goToScreen(
    ArtistSongsScreen(
      artistId = artistId,
      artistType = artistType,
      artistName = artistName,
      artwork = artistArt.value,
      backTo = fetch(R.string.AllAlbumArtistSongs)
    )
  )
}
