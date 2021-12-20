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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoEmptyResult
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.asAlbumIdList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.nav.goToScreen
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
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Parcelize
@Immutable
data class ArtistAlbumsScreen(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val songCount: Int
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        ArtistAlbumsViewModel(
          artistId = artistId,
          artistType = artistType,
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

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      LibraryItemsActions(
        itemCount = albums.value.size,
        selectedItems = selected.value,
        viewModel = viewModel
      )
      AlbumsList(
        list = albums.value,
        selectedItems = selected.value,
        itemClicked = { viewModel.itemClicked(it) },
        itemLongClicked = { viewModel.itemLongClicked(it) },
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
        painter = rememberImagePainter(data = artistType.typeIcon),
        contentDescription = stringResource(id = artistType.allSongsRes),
        modifier = Modifier.size(40.dp),
        tint = LocalContentColor.current
      )
    },
    text = {
      Text(
        text = stringResource(id = artistType.allSongsRes),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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

private class ArtistAlbumsViewModel(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  albumDao: AlbumDao,
  private val audioMediaDao: AudioMediaDao,
  localAudioQueueModel: LocalAudioQueueViewModel,
  backstack: Backstack
) : BaseAlbumsViewModel(albumDao, backstack, localAudioQueueModel) {
  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): Result<List<AlbumDescription>, DaoMessage> =
    albumDao.getAllAlbumsFor(artistId, artistType)

  override fun goToAlbumSongs(albumId: AlbumId) =
    backstack.goToScreen(AlbumSongsForArtistScreen(albumId, artistId))

  override suspend fun makeCategoryMediaList(
    albumList: List<AlbumsViewModel.AlbumInfo>
  ) = audioMediaDao
    .getMediaForAlbums(
      albumList
        .mapTo(LongArrayList(512)) { it.id.value }
        .asAlbumIdList,
      artistId
    )
    .toErrorIf({ idList -> idList.isEmpty() }) { DaoEmptyResult }
    .map { idList -> CategoryMediaList(idList, CategoryToken(albumList.last().id)) }

  fun goToArtistSongs() = backstack.goToScreen(ArtistSongsScreen(artistId, artistType))
}
