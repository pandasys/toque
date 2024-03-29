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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.data.AlbumInfo
import com.ealva.toque.ui.theme.toqueColors
import com.google.accompanist.insets.LocalWindowInsets
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsList(
  list: List<AlbumInfo>,
  selectedItems: SelectedItems<AlbumId>,
  itemClicked: (AlbumInfo) -> Unit,
  itemLongClicked: (AlbumInfo) -> Unit,
  header: (@Composable LazyItemScope.() -> Unit)? = null
) {
  val config = LocalScreenConfig.current
  val listState = rememberLazyListState()

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
      .padding(top = 18.dp, bottom = config.getNavPlusBottomSheetHeight(isExpanded = true))
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      )
    ) {
      if (header != null) item(key = "UnchangingHeader", content = header)
      items(items = list, key = { it.id }) { albumInfo ->
        AlbumItem(
          albumInfo = albumInfo,
          isSelected = selectedItems.isSelected(albumInfo.id),
          modifier = Modifier.combinedClickable(
            onClick = { itemClicked(albumInfo) },
            onLongClick = { itemLongClicked(albumInfo) }
          ),
        )
      }
    }
  }
}

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AlbumItem(
  albumInfo: AlbumInfo,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(toqueColors.selectedBackground) },
    icon = { ListItemAlbumArtwork(artwork = albumInfo.artwork) },
    text = { ListItemText(text = albumInfo.title.value) },
    overlineText = { ArtistAndSongCount(albumInfo = albumInfo) },
    secondaryText = {
      CountDurationYear(albumInfo.songCount, albumInfo.duration, albumInfo.year)
    }
  )
}

@Composable
private fun ArtistAndSongCount(albumInfo: AlbumInfo) {
  Text(
    text = albumInfo.artist.value,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier
  )
}

@Preview
@Composable
fun AlbumsListPreview() {
  val list = listOf(
    AlbumInfo(
      AlbumId(1),
      AlbumTitle("Album Title 1"),
      ArtistName("Artist Name 1"),
      1970,
      Uri.EMPTY,
      5,
      20.minutes
    ),
    AlbumInfo(
      AlbumId(1),
      AlbumTitle("Album Title 2"),
      ArtistName("Artist Name 2"),
      1980,
      Uri.EMPTY,
      100,
      5.hours
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
