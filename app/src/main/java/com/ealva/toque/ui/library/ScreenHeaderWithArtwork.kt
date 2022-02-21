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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.toque.R
import com.ealva.toque.ui.common.LocalScreenConfig


private val MIN_IMAGE_HEIGHT_PORTRAIT = 240.dp
private val MIN_IMAGE_HEIGHT_LANDSCAPE = 150.dp

@Composable
fun ScreenHeaderWithArtwork(
  modifier: Modifier = Modifier,
  artwork: Uri,
  content: @Composable () -> Unit,
) {
  val screenConfig = LocalScreenConfig.current
  val inPortrait = screenConfig.inPortrait
  Layout(
    modifier = modifier,
    content = {
      Image(
        painter = rememberImagePainter(
          data = artwork,
          builder = {
            error(R.drawable.ic_big_album)
          }
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
    val minHeight = if (inPortrait) MIN_IMAGE_HEIGHT_PORTRAIT else MIN_IMAGE_HEIGHT_LANDSCAPE
    val contentPlaceable = measurables[1].measure(
      Constraints(
        minWidth = contentWidth,
        maxWidth = contentWidth,
        minHeight = minHeight.roundToPx()
      )
    )

    val imageHeight = contentPlaceable.height.coerceAtLeast(minHeight.roundToPx())
    val imagePlaceable =
      measurables[0].measure(Constraints.fixed(constraints.maxWidth, imageHeight))

    layout(width = constraints.maxWidth, height = imageHeight) {
      imagePlaceable.placeRelative(x = 0, y = 0)
      contentPlaceable.placeRelative(x = 0, y = 0)
    }
  }
}
