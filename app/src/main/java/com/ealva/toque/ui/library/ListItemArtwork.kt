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

package com.ealva.toque.ui.library

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ealva.toque.R

@Composable
fun ListItemArtwork(
  artwork: Uri,
  @DrawableRes fallback: Int,
  fallbackTint: Color = LocalContentColor.current
) {
  val modifier = Modifier.size(56.dp)
  val description = stringResource(id = R.string.Artwork)
  if (artwork !== Uri.EMPTY) {
    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(LocalContext.current)
        .data(artwork)
        .build(),
      contentScale = ContentScale.Fit
    )
    Image(
      modifier = modifier,
      painter = painter,
      contentDescription = description
    )
    if (painter.state is AsyncImagePainter.State.Error) {
      Icon(
        modifier = modifier,
        painter = painterResource(id = fallback),
        contentDescription = description,
        tint = fallbackTint
      )
    }
  } else {
    Icon(
      modifier = modifier,
      painter = painterResource(id = fallback),
      contentDescription = description,
      tint = fallbackTint
    )
  }
}

@Composable
fun ListItemAlbumArtwork(artwork: Uri) {
  Image(
    painter = if (artwork !== Uri.EMPTY)
      rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
          .data(artwork)
          .error(R.drawable.ic_big_album)
          .build(),
        contentScale = ContentScale.Fit
      )
    else painterResource(id = R.drawable.ic_big_album),
    contentDescription = stringResource(R.string.Artwork),
    modifier = Modifier.size(56.dp)
  )
}
