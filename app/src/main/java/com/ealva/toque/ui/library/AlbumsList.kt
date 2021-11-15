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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.R
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
import com.ealva.toque.ui.theme.toqueColors
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsList(
  list: List<AlbumsViewModel.AlbumInfo>,
  selectedItems: SelectedItems<AlbumId>,
  itemClicked: (AlbumId) -> Unit,
  itemLongClicked: (AlbumId) -> Unit,
  header: (@Composable LazyItemScope.() -> Unit)? = null
) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current

  LibraryScrollBar(listState = listState) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      ),
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      if (header != null) item(key = "UnchangingHeader", content = header)
      items(items = list, key = { it.id }) { albumInfo ->
        AlbumItem(
          albumInfo = albumInfo,
          isSelected = selectedItems.isSelected(albumInfo.id),
          itemClicked = { albumId -> itemClicked(albumId) },
          itemLongClicked = { albumId -> itemLongClicked(albumId) }
        )
      }
    }
  }
}

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AlbumItem(
  albumInfo: AlbumsViewModel.AlbumInfo,
  isSelected: Boolean,
  itemClicked: (AlbumId) -> Unit,
  itemLongClicked: (AlbumId) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(MaterialTheme.toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(albumInfo.id) },
        onLongClick = { itemLongClicked(albumInfo.id) }
      ),
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
private fun ArtistAndSongCount(albumInfo: AlbumsViewModel.AlbumInfo) {
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

@Preview
@Composable
fun AlbumsListPreview() {
  val list = listOf(
    AlbumsViewModel.AlbumInfo(
      AlbumId(1),
      AlbumTitle("Album Title 1"),
      Uri.EMPTY,
      ArtistName("Artist Name 1"),
      5
    ),
    AlbumsViewModel.AlbumInfo(
      AlbumId(1),
      AlbumTitle("Album Title 2"),
      Uri.EMPTY,
      ArtistName("Artist Name 2"),
      100
    )
  )
  ProvideScreenConfig(
    screenConfig = makeScreenConfig(
      LocalConfiguration.current,
      LocalDensity.current,
      LocalWindowInsets.current
    )
  ) {
    AlbumsList(list = list, SelectedItems(), itemClicked = {}, itemLongClicked = {})
  }
}
