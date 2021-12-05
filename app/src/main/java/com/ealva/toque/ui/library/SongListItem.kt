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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.asDurationString
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.ui.common.modifyIf
import com.ealva.toque.ui.theme.toqueColors
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SongListItem(
  songTitle: Title,
  albumTitle: AlbumTitle,
  artistName: ArtistName,
  songDuration: Millis,
  rating: Rating,
  highlightBackground: Boolean,
  icon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  textColor: Color = Color.Unspecified
) {
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .modifyIf(highlightBackground) { background(MaterialTheme.toqueColors.selectedBackground) },
    icon = icon,
    text = { TitleText(songTitle, textColor) },
    secondaryText = { ArtistAndDuration(artistName, songDuration, textColor) },
    overlineText = { AlbumAndRating(albumTitle, rating, textColor) }
  )
}

@Composable
private fun TitleText(songTitle: Title, textColor: Color) {
  Text(text = songTitle.value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = textColor)
}

@Composable
fun SongListItemIcon(artwork: Uri) {
  Image(
    painter = rememberImagePainter(
      data = artwork,
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
private fun ArtistAndDuration(artistName: ArtistName, songDuration: Millis, textColor: Color) {
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
      text = songDuration.asDurationString,
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
    RatingBar(
      value = rating.toStarRating().value,
      size = 8.dp,
      padding = 2.dp,
      isIndicator = true,
      activeColor = if (textColor.isUnspecified) LocalContentColor.current else textColor,
      inactiveColor = if (textColor.isUnspecified) LocalContentColor.current else textColor,
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
