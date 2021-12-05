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
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
import com.google.accompanist.insets.LocalWindowInsets

@Composable
fun LibraryActionBar(
  itemCount: Int,
  inSelectionMode: Boolean,
  selectedCount: Int,
  play: () -> Unit,
  shuffle: () -> Unit,
  playNext: () -> Unit,
  addToUpNext: () -> Unit,
  addToPlaylist: () -> Unit,
  startSearch: () -> Unit = {},
  selectAllOrNone: (Boolean) -> Unit = {},
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  val config = LocalScreenConfig.current
  val buttonHeight = config.actionBarButtonHeight
  val enabled = itemCount > 0
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
        .weight(1F)
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_play,
        description = R.string.Play,
        onClick = play,
        enabled = enabled
      )
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_shuffle,
        description = R.string.Shuffle,
        onClick = shuffle,
        enabled = enabled
      )
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_queue_play_next,
        description = R.string.PlayNext,
        onClick = playNext,
        enabled = enabled
      )
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_add_to_queue,
        description = R.string.AddToUpNext,
        onClick = addToUpNext,
        enabled = enabled
      )
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_playlist_add,
        description = R.string.AddToPlaylist,
        onClick = addToPlaylist,
        enabled = enabled
      )
      ActionButton(
        buttonHeight = buttonHeight,
        modifier = buttonModifier,
        drawable = R.drawable.ic_search,
        description = R.string.Search,
        onClick = startSearch,
        enabled = enabled
      )
    }
    if (inSelectionMode && enabled) {
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
          style = MaterialTheme.typography.caption,
        )
        if (selectActions != null) selectActions(24.dp)
        Spacer(
          modifier = Modifier.weight(1F)
        )
        Text(
          text = "$selectedCount/$itemCount",
          textAlign = TextAlign.End,
          maxLines = 1,
          style = MaterialTheme.typography.caption,
        )
      }
    }
  }
}

@Preview
@Composable
private fun LibraryItemsActionRowPreview() {
  ProvideScreenConfig(
    screenConfig = makeScreenConfig(
      LocalConfiguration.current,
      LocalDensity.current,
      LocalWindowInsets.current
    )
  ) {
    LibraryActionBar(
      10,
      false,
      0,
      play = {},
      shuffle = {},
      playNext = {},
      addToUpNext = {},
      addToPlaylist = {},
      selectAllOrNone = {},
      startSearch = {}
    )
  }
}
