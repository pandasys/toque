/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.ui.now

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ealva.toque.R
import com.ealva.toque.ui.library.ArtistType
import com.ealva.toque.ui.library.SongListItem
import com.ealva.toque.ui.library.SongListItemIcon
import com.ealva.toque.ui.now.NowPlayingViewModel.QueueItem
import com.ealva.toque.ui.theme.toqueTypography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CurrentItemDialog(
  audioItem: QueueItem,
  onDismiss: () -> Unit,
  showMediaInfo: (QueueItem) -> Unit,
  selectAlbumArt: (QueueItem) -> Unit,
  goToAlbum: (QueueItem) -> Unit,
  goToArtist: (QueueItem) -> Unit,
  goToAlbumArtist: (QueueItem) -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.padding(8.dp),
      shape = RoundedCornerShape(10),
      elevation = 1.dp
    ) {
      Column {
        SongListItem(
          songTitle = audioItem.title,
          albumTitle = audioItem.albumTitle,
          artistName = audioItem.artist,
          highlightBackground = false,
          icon = { SongListItemIcon(audioItem.artwork) },
        )
        LazyVerticalGrid(
          cells = GridCells.Fixed(2),
          modifier = Modifier.padding(horizontal = 8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
          item {
            DialogItem(
              title = R.string.Info,
              icon = R.drawable.ic_info,
              onClick = { showMediaInfo(audioItem) }
            )
          }
          item {
            DialogItem(
              title = R.string.AlbumArt,
              icon = R.drawable.ic_image,
              onClick = { selectAlbumArt(audioItem) }
            )
          }
          item {
            DialogItem(
              title = R.string.Album,
              icon = R.drawable.ic_album,
              onClick = { goToAlbum(audioItem) }
            )
          }
          item {
            DialogItem(
              title = R.string.Artist,
              icon = ArtistType.SongArtist.typeIcon,
              onClick = { goToArtist(audioItem) }
            )
          }
          item {
            DialogItem(
              title = R.string.AlbumArtist,
              icon = ArtistType.AlbumArtist.typeIcon,
              onClick = { goToAlbumArtist(audioItem) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DialogItem(
  @StringRes title: Int,
  @DrawableRes icon: Int,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(id = icon),
      contentDescription = stringResource(id = title),
      modifier = Modifier.size(24.dp),
      tint = LocalContentColor.current
    )
    Text(
      text = stringResource(id = title),
      style = toqueTypography.body2,
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(start = 12.dp)
    )
  }
}
