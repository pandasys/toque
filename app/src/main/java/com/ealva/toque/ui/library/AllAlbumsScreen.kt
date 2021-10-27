/*
 * Copyright 2021 eAlva.com
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.AllAlbumsViewModel.AlbumInfo
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(AllAlbumsScreen::class)

@Immutable
@Parcelize
data class AllAlbumsScreen(
  private val noArg: String = ""
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllAlbumsViewModel(get())) }
  }
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllAlbumsViewModel>()
    val albums = viewModel.allAlbums.collectAsState()
    AllAlbumsList(albums.value)
  }
}

@Composable
private fun AllAlbumsList(list: List<AlbumInfo>) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current
  val sheetHeight = config.getNavPlusBottomSheetHeight(isExpanded = true)

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = sheetHeight, end = 8.dp),
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
  ) {
    items(list) { albumInfo -> AlbumItem(albumInfo = albumInfo) }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AlbumItem(albumInfo: AlbumInfo) {
  ListItem(
    modifier = Modifier.fillMaxWidth(),
    icon = {
      Image(
        painter = rememberImagePainter(
          data = albumInfo.artwork,
          builder = {
            error(R.drawable.ic_big_album)
            placeholder(R.drawable.ic_big_album)
          }
        ),
        contentDescription = stringResource(R.string.AlbumArt),
        modifier = Modifier.size(40.dp)
      )
    },
    text = { Text(text = albumInfo.title.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = { ArtistAndSongCount(albumInfo = albumInfo) },
  )
}

@Composable
private fun ArtistAndSongCount(albumInfo: AlbumInfo) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (artist, count) = createRefs()
    Text(
      text = albumInfo.artist.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(artist) {
        start.linkTo(parent.start)
        end.linkTo(count.start)
        width = Dimension.fillToConstraints
      }
    )
    Text(
      text = LocalContext.current.resources.getQuantityString(
        R.plurals.SongCount,
        albumInfo.songCount,
        albumInfo.songCount,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(count) {
        start.linkTo(artist.end)
        end.linkTo(parent.end)
        width = Dimension.wrapContent
      }
    )
  }
}

private interface AllAlbumsViewModel {
  data class AlbumInfo(
    val id: AlbumId,
    val title: AlbumTitle,
    val artwork: Uri,
    val artist: ArtistName,
    val songCount: Int
  )

  val allAlbums: StateFlow<List<AlbumInfo>>

  companion object {
    operator fun invoke(albumDao: AlbumDao): AllAlbumsViewModel = AllAlbumsViewModelImpl(albumDao)
  }
}

private class AllAlbumsViewModelImpl(
  private val albumDao: AlbumDao
) : AllAlbumsViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val allAlbums = MutableStateFlow<List<AlbumInfo>>(emptyList())

  override fun onServiceActive() {
    scope = CoroutineScope(Job() + Dispatchers.Main)
    scope.launch {
      when (val result = albumDao.getAllAlbums(Long.MAX_VALUE)) {
        is Ok -> handleAlbumList(result.value)
        is Err -> LOG._e { it("%s", result.error) }
      }
    }
  }

  private fun handleAlbumList(list: List<AlbumDescription>) {
    LOG._e { it("list.size=%d", list.size) }
    allAlbums.value = list.mapTo(ArrayList(list.size)) {
      AlbumInfo(
        id = it.albumId,
        title = it.albumTitle,
        artwork = if (it.albumLocalArt !== Uri.EMPTY) it.albumLocalArt else it.albumArt,
        artist = it.artistName,
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    allAlbums.value = emptyList()
  }
}
