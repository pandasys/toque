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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.persist.MediaId
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.library.data.SongInfo
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongItemList(
  list: List<SongInfo>,
  selectedItems: SelectedItems<MediaId>,
  itemClicked: (SongInfo) -> Unit,
  itemLongClicked: (SongInfo) -> Unit,
  modifier: Modifier = Modifier
) {
  val listState: LazyListState = rememberLazyListState()
  val config = LocalScreenConfig.current

  listState.isScrollInProgress

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
      .padding(top = 18.dp, bottom = config.getNavPlusBottomSheetHeight(isExpanded = true))
      .then(modifier)
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      ),
    ) {
      items(items = list, key = { it.id.value }) { songInfo ->
        SongListItem(
          songTitle = songInfo.title,
          albumTitle = songInfo.album,
          artistName = songInfo.artist,
          songDuration = songInfo.duration,
          rating = songInfo.rating,
          highlightBackground = selectedItems.isSelected(songInfo.id),
          icon = { ListItemAlbumArtwork(songInfo.artwork) },
          modifier = Modifier.combinedClickable(
            onClick = { itemClicked(songInfo) },
            onLongClick = { itemLongClicked(songInfo) }
          )
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun SongItemListPreview() {
  val list = listOf(
    SongInfo(
      id = MediaId(1),
      title = Title("A Song title"),
      duration = 178000.toDuration(DurationUnit.MILLISECONDS),
      rating = Rating.RATING_4,
      album = AlbumTitle("Album Title 1"),
      artist = ArtistName("Artist Name 1"),
      Uri.EMPTY
    ),
    SongInfo(
      id = MediaId(2),
      title = Title("Happy Little Ditty"),
      duration = 500.toDuration(DurationUnit.MILLISECONDS),
      rating = Rating.RATING_4_5,
      album = AlbumTitle("Rock Thrash"),
      artist = ArtistName("Princess"),
      Uri.EMPTY
    ),
    SongInfo(
      id = MediaId(3),
      title = Title("Instead Here"),
      duration = 1178000.toDuration(DurationUnit.MILLISECONDS),
      rating = Rating.RATING_5,
      album = AlbumTitle("Metal Country Soulish"),
      artist = ArtistName("Harvey Wilder"),
      Uri.EMPTY
    ),
  )

  ProvideScreenConfig(
    screenConfig = makeScreenConfig(
      LocalConfiguration.current,
      LocalDensity.current,
      LocalWindowInsets.current
    )
  ) {
    SongItemList(
      list = list,
      selectedItems = SelectedItems(),
      itemClicked = {},
      itemLongClicked = {},
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    )
  }
}
