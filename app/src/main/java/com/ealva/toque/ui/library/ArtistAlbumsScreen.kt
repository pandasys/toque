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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.R
import com.ealva.toque.common.Filter
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.github.michaelbull.result.Result
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Parcelize
@Immutable
data class ArtistAlbumsScreen(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val artistName: ArtistName,
  private val songCount: Int
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(ArtistAlbumsViewModel(artistId, artistType, artistName, get(), backstack))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<ArtistAlbumsViewModel>()
    val albums = viewModel.albumList.collectAsState()
    val selected = viewModel.selectedItems.asState()
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
      .background(Color.Black)
      .clickable(onClick = { doAllSongsSelected() }),
    icon = {
      Icon(
        painter = rememberImagePainter(data = artistType.typeIcon),
        contentDescription = stringResource(id = artistType.allSongsRes),
        modifier = Modifier.size(40.dp)
      )
    },
    text = {
      Text(
        text = stringResource(id = artistType.allSongsRes),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    },
    secondaryText = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.SongCount,
          songCount,
          songCount,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis
      )
    },
  )
}

private class ArtistAlbumsViewModel(
  private val artistId: ArtistId,
  private val artistType: ArtistType,
  private val artistName: ArtistName,
  albumDao: AlbumDao,
  backstack: Backstack
) : BaseAlbumsViewModel(albumDao, backstack) {
  override suspend fun doGetAlbums(
    albumDao: AlbumDao,
    filter: Filter
  ): Result<List<AlbumDescription>, DaoMessage> =
    albumDao.getAllAlbumsFor(artistId, artistType)

  override fun goToAlbumSongs(albumId: AlbumId) {
    val title = albumList.value
      .find { it.id == albumId }
      ?.title ?: AlbumTitle("")
    backstack.goTo(AlbumSongsForArtistScreen(albumId, artistId, title))
  }

  fun goToArtistSongs() = backstack.goTo(ArtistSongsScreen(artistId, artistType, artistName))
}
