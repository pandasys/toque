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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Rating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asHourMinutesSeconds
import com.ealva.toque.common.toStarRating
import com.ealva.toque.ui.common.ListItemText
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.library.data.SongInfo
import com.ealva.toque.ui.theme.toqueColors
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarConfig
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize
import kotlin.time.Duration

@ExperimentalFoundationApi
@Composable
fun SongListItem(
  songInfo: SongInfo,
  highlightBackground: Boolean,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  textColor: Color = Color.Unspecified
) {
  SongListItem(
    songTitle = songInfo.title,
    albumTitle = songInfo.album,
    artistName = songInfo.artist,
    songDuration = songInfo.duration,
    rating = songInfo.rating,
    highlightBackground = highlightBackground,
    icon = icon,
    modifier = modifier,
    textColor = textColor
  )
}

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongListItem(
  songTitle: Title,
  albumTitle: AlbumTitle,
  artistName: ArtistName,
  songDuration: Duration,
  rating: Rating,
  highlightBackground: Boolean,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  textColor: Color = Color.Unspecified
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(highlightBackground) { background(toqueColors.selectedBackground) },
    icon = icon,
    text = { ListItemText(text = songTitle.value, color = textColor) },
    secondaryText = { ArtistAndDuration(artistName, songDuration, textColor) },
    overlineText = { AlbumAndRating(albumTitle, rating, textColor) }
  )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongListItem(
  songTitle: Title,
  albumTitle: AlbumTitle,
  artistName: ArtistName,
  highlightBackground: Boolean,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  textColor: Color = Color.Unspecified
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(highlightBackground) { background(toqueColors.selectedBackground) },
    icon = icon,
    text = { ListItemText(text = songTitle.value, color = textColor) },
    secondaryText = {
      Text(
        text = artistName.value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = textColor
      )
    },
    overlineText = {
      Text(
        text = albumTitle.value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = textColor,
      )
    }
  )
}

@Composable
private fun ArtistAndDuration(artistName: ArtistName, songDuration: Duration, textColor: Color) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (artist, duration) = createRefs()
    Text(
      text = artistName.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = textColor,
      modifier = Modifier.constrainAs(artist) {
        start.linkTo(parent.start)
        end.linkTo(duration.start)
        width = Dimension.fillToConstraints
      }
    )
    Text(
      text = songDuration.asHourMinutesSeconds,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = textColor,
      modifier = Modifier.constrainAs(duration) {
        start.linkTo(artist.end)
        end.linkTo(parent.end)
        width = Dimension.wrapContent
      }
    )
  }
}

@Composable
private fun AlbumAndRating(albumTitle: AlbumTitle, rating: Rating, textColor: Color) {
  ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
    val (text, ratingBar) = createRefs()
    Text(
      text = albumTitle.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = textColor,
      modifier = Modifier.constrainAs(text) {
        start.linkTo(parent.start)
        end.linkTo(ratingBar.start)
        width = Dimension.fillToConstraints
      }
    )
    val rbColor = if (textColor.isUnspecified) LocalContentColor.current else textColor
    RatingBar(
      value = rating.toStarRating().value,
      config = RatingBarConfig()
        .size(8.dp)
        .padding(2.dp)
        .isIndicator(true)
        .activeColor(rbColor)
        .inactiveColor(rbColor)
        .stepSize(StepSize.HALF)
        .style(RatingBarStyle.HighLighted),
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
