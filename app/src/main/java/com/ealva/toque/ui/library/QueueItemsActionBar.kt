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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.LocalWindowInsets

@Composable
fun QueueItemsActionBar(
  itemCount: Int,
  inSelectionMode: Boolean,
  selectedCount: Int,
  goToCurrent: () -> Unit,
  goToTop: () -> Unit,
  goToBottom: () -> Unit,
  addToPlaylist: () -> Unit,
  startSearch: () -> Unit = {},
  selectAllOrNone: (Boolean) -> Unit = {},
  mediaInfoClicked: () -> Unit,
) {
  val config = LocalScreenConfig.current
  val buttonHeight = config.actionBarButtonHeight
  val buttonColors = ButtonDefaults.outlinedButtonColors(
    backgroundColor = Color.Transparent,
    contentColor = LocalContentColor.current,
    disabledContentColor = LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
  )
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(buttonHeight + 4.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val buttonModifier = Modifier
        .height(buttonHeight)
        .width(buttonHeight * 1.4F)
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_go_to_current,
        description = R.string.GoToCurrent,
        colors = buttonColors,
        onClick = goToCurrent,
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_vertical_align_top,
        description = R.string.GoToTop,
        colors = buttonColors,
        onClick = goToTop
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_vertical_align_bottom,
        description = R.string.GoToBottom,
        colors = buttonColors,
        onClick = goToBottom
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_playlist_add,
        description = R.string.AddToPlaylist,
        colors = buttonColors,
        onClick = addToPlaylist
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_search,
        description = R.string.Search,
        colors = buttonColors,
        onClick = startSearch
      )
    }
    if (inSelectionMode) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = (itemCount == selectedCount),
          onCheckedChange = selectAllOrNone
        )
        Text(
          modifier = Modifier.padding(start = 8.dp),
          text = "All",
          textAlign = TextAlign.End,
          maxLines = 1,
          style = toqueTypography.caption,
        )
        ActionButton(
          modifier = Modifier.height(24.dp),
          iconSize = 24.dp,
          drawable = R.drawable.ic_info,
          description = R.string.MediaInfo,
          enabled = selectedCount == 1,
          colors = buttonColors,
          onClick = mediaInfoClicked
        )
        Spacer(
          modifier = Modifier.weight(1F)
        )
        Text(
          text = "$selectedCount/$itemCount",
          textAlign = TextAlign.End,
          maxLines = 1,
          style = toqueTypography.caption,
        )
      }
    }
  }
}

@Preview
@Composable
fun QueueItemsActionBarPreview() {
  ProvideScreenConfig(
    screenConfig = makeScreenConfig(
      LocalConfiguration.current,
      LocalDensity.current,
      LocalWindowInsets.current
    )
  ) {
    QueueItemsActionBar(
      10,
      true,
      2,
      goToCurrent = {},
      goToTop = {},
      goToBottom = {},
      addToPlaylist = {},
      startSearch = {},
      selectAllOrNone = {},
      mediaInfoClicked = {}
    )
  }
}
