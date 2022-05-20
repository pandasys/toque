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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ealva.toque.R

@Composable
fun ScreenHeaderWithArtwork(
  modifier: Modifier = Modifier,
  artwork: Uri,
  @DrawableRes fallbackArt: Int = R.drawable.ic_big_album,
  content: @Composable () -> Unit,
) {
  Layout(
    modifier = modifier,
    content = {
      Image(
        painter = rememberAsyncImagePainter(
          model = ImageRequest.Builder(LocalContext.current)
            .data(artwork)
            .error(fallbackArt)
            .build(),
          contentScale = ContentScale.Crop
        ),
        alignment = Alignment.Center,
        contentScale = ContentScale.Crop,
        contentDescription = stringResource(R.string.Artwork),
        modifier = Modifier.fillMaxWidth()
      )
      content()
    }
  ) { measurables, constraints ->
    val contentWidth = constraints.maxWidth
    val contentPlaceable = measurables[1].measure(
      Constraints(
        minWidth = contentWidth,
        maxWidth = contentWidth,
      )
    )

    val imageHeight = contentPlaceable.height
    val imagePlaceable =
      measurables[0].measure(Constraints.fixed(constraints.maxWidth, imageHeight))

    layout(width = constraints.maxWidth, height = imageHeight) {
      imagePlaceable.placeRelative(x = 0, y = 0)
      contentPlaceable.placeRelative(x = 0, y = 0)
    }
  }
}
