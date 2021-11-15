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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toDurationString
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
import com.ealva.toque.ui.theme.toqueColors
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongItemList(
  list: List<SongsViewModel.SongInfo>,
  selectedItems: SelectedItems<MediaId>,
  itemClicked: (MediaId) -> Unit,
  itemLongClicked: (MediaId) -> Unit
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
      items(items = list, key = { it.id }) { songInfo ->
        SongItem(
          songInfo = songInfo,
          isSelected = selectedItems.isSelected(songInfo.id),
          itemClicked = { itemClicked(it) },
          itemLongClicked = { itemLongClicked(it) }
        )
      }
    }
  }
}

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SongItem(
  songInfo: SongsViewModel.SongInfo,
  isSelected: Boolean,
  itemClicked: (MediaId) -> Unit,
  itemLongClicked: (MediaId) -> Unit
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .modifyIf(isSelected) { background(MaterialTheme.toqueColors.selectedBackground) }
      .combinedClickable(
        onClick = { itemClicked(songInfo.id) },
        onLongClick = { itemLongClicked(songInfo.id) }
      ),
    icon = { SongArtwork(songInfo) },
    text = { Text(text = songInfo.title.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = { ArtistAndDuration(songInfo) },
    overlineText = { AlbumAndRating(songInfo) }
  )
}
@Composable
private fun SongArtwork(songInfo: SongsViewModel.SongInfo) {
  Image(
    painter = rememberImagePainter(
      data = songInfo.artwork,
      builder = {
        error(R.drawable.ic_big_album)
        placeholder(R.drawable.ic_big_album)
      }
    ),
    contentDescription = "Toggle Equalizer",
    modifier = Modifier.size(56.dp)
  )
}
@Composable
private fun ArtistAndDuration(songInfo: SongsViewModel.SongInfo) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (artist, duration) = createRefs()
    Text(
      text = songInfo.artist.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(artist) {
        start.linkTo(parent.start)
        end.linkTo(duration.start)
        width = Dimension.fillToConstraints
      }
    )
    Text(
      text = songInfo.duration.toDurationString(),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(duration) {
        start.linkTo(artist.end)
        end.linkTo(parent.end)
        width = Dimension.wrapContent
      }
    )
  }
}
@Composable
private fun AlbumAndRating(songInfo: SongsViewModel.SongInfo) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (text, ratingBar) = createRefs()
    Text(
      text = songInfo.album.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.constrainAs(text) {
        start.linkTo(parent.start)
        end.linkTo(ratingBar.start)
        width = Dimension.fillToConstraints
      }
    )
    RatingBar(
      value = songInfo.rating.toStarRating().value,
      size = 8.dp,
      padding = 2.dp,
      isIndicator = true,
      activeColor = Color.White,
      inactiveColor = Color.White,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {},
      onRatingChanged = {},
      modifier = Modifier.constrainAs(ratingBar) {
        top.linkTo(text.top)
        start.linkTo(text.end)
        end.linkTo(parent.end)
        bottom.linkTo(text.bottom)
        width = Dimension.wrapContent
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun SongItemListPreview() {
  val list = listOf(
    SongsViewModel.SongInfo(
      id = MediaId(1),
      title = Title("A Song title"),
      duration = Millis(178000),
      rating = Rating.RATING_4,
      album = AlbumTitle("Album Title 1"),
      artist = ArtistName("Artist Name 1"),
      Uri.EMPTY
    ),
    SongsViewModel.SongInfo(
      id = MediaId(2),
      title = Title("Happy Little Ditty"),
      duration = Millis(500),
      rating = Rating.RATING_4_5,
      album = AlbumTitle("Rock Thrash"),
      artist = ArtistName("Princess"),
      Uri.EMPTY
    ),
    SongsViewModel.SongInfo(
      id = MediaId(3),
      title = Title("Instead Here"),
      duration = Millis(1178000),
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
      itemLongClicked = {}
    )
  }
}
