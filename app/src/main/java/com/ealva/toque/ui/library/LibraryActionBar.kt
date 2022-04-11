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
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
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
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.OvalBackground
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.TextOvalBackground
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.LocalWindowInsets

@Composable
fun LibraryActionBar(
  modifier: Modifier = Modifier,
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
  buttonColors: ButtonColors,
  selectActions: (@Composable (Dp) -> Unit)? = null,
) {
  val contentColor = buttonColors.contentColor(enabled = true).value
  val disabledColor = buttonColors.contentColor(enabled = false).value

  val config = LocalScreenConfig.current
  val buttonHeight = config.actionBarButtonHeight
  val enabled = itemCount > 0
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(buttonHeight + 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val buttonModifier = Modifier
        .height(buttonHeight)
        .width(buttonHeight * 1.4F)
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_play,
        description = R.string.Play,
        enabled = enabled,
        colors = buttonColors,
        onClick = play
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_shuffle,
        description = R.string.Shuffle,
        enabled = enabled,
        colors = buttonColors,
        onClick = shuffle
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_queue_play_next,
        description = R.string.PlayNext,
        enabled = enabled,
        colors = buttonColors,
        onClick = playNext
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_add_to_queue,
        description = R.string.AddToUpNext,
        enabled = enabled,
        colors = buttonColors,
        onClick = addToUpNext
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_playlist_add,
        description = R.string.AddToPlaylist,
        enabled = enabled,
        colors = buttonColors,
        onClick = addToPlaylist
      )
      ActionButton(
        modifier = buttonModifier,
        iconSize = buttonHeight,
        drawable = R.drawable.ic_search,
        description = R.string.Search,
        enabled = enabled,
        colors = buttonColors,
        onClick = startSearch
      )
    }
    if (inSelectionMode && enabled) {
      val backgroundColor = buttonColors.backgroundColor(enabled = true).value
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
      ) {
        OvalBackground(ovalColor = backgroundColor) {
          Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = (itemCount == selectedCount),
              onCheckedChange = selectAllOrNone,
              colors = CheckboxDefaults.colors(
                uncheckedColor = contentColor,
                disabledColor = disabledColor
              )
            )
            Text(
              modifier = Modifier.padding(end = 6.dp),
              text = "All",
              textAlign = TextAlign.End,
              maxLines = 1,
              color = contentColor,
              style = toqueTypography.caption,
            )
          }
        }
        Spacer(modifier = Modifier.width(6.dp))
        if (selectActions != null) selectActions(24.dp)
        Spacer(
          modifier = Modifier.weight(1F)
        )
        TextOvalBackground(
          text = "$selectedCount/$itemCount",
          maxLines = 1,
          style = toqueTypography.caption,
          color = contentColor,
          ovalColor = backgroundColor
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
      itemCount = 10,
      inSelectionMode = false,
      selectedCount = 0,
      play = {},
      shuffle = {},
      playNext = {},
      addToUpNext = {},
      addToPlaylist = {},
      selectAllOrNone = {},
      startSearch = {},
      buttonColors = ButtonDefaults.outlinedButtonColors()
    )
  }
}
