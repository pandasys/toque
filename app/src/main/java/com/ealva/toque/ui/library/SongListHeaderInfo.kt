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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.ealva.toque.persist.MediaId
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.TextOvalBackground
import com.ealva.toque.ui.common.timesAlpha
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.statusBarsPadding

@Composable
fun SongListHeaderInfo(
  title: String,
  subtitle: String?,
  itemCount: Int,
  selectedItems: SelectedItems<MediaId>,
  viewModel: SongsViewModel,
  buttonColors: ButtonColors,
  backTo: String,
  scrollConnection: HeightResizeScrollConnection,
  back: () -> Unit
) {
  val heightSubtrahend = scrollConnection.heightSubtrahend.collectAsState()
  SongListHeaderInfo(
    title = title,
    subtitle = subtitle,
    itemCount = itemCount,
    selectedItems = selectedItems,
    viewModel = viewModel,
    buttonColors = buttonColors,
    backTo = backTo,
    heightSubtrahend = heightSubtrahend.value,
    scrollConnection = scrollConnection,
    back = back
  )
}

@Composable
private fun SongListHeaderInfo(
  title: String,
  subtitle: String?,
  itemCount: Int,
  selectedItems: SelectedItems<MediaId>,
  viewModel: SongsViewModel,
  buttonColors: ButtonColors,
  backTo: String,
  heightSubtrahend: Int,
  scrollConnection: HeightResizeScrollConnection,
  back: () -> Unit
) {
  val maxSubtrahend = scrollConnection.maxSubtrahend
  val alphaPercentage =
    if (maxSubtrahend < Int.MAX_VALUE) 1F - (heightSubtrahend.toFloat() / maxSubtrahend) else 1F
  val screenConfig = LocalScreenConfig.current
  Layout(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp),
    content = {
      val contentColor = buttonColors.contentColor(enabled = true).value.timesAlpha(alphaPercentage)
      val ovalColor: Color = toqueColors.shadedBackground.timesAlpha(alphaPercentage)

      val notInSelectionMode = !selectedItems.inSelectionMode
      if (notInSelectionMode) {
        Column {
          Spacer(modifier = Modifier.height(2.dp))
          BackToButton(
            modifier = Modifier,
            backTo = backTo,
            color = contentColor,
            ovalColor = ovalColor,
            back = back
          )
        }
      }

      TextOvalBackground(
        modifier = Modifier.padding(vertical = 2.dp),
        text = title,
        color = contentColor,
        ovalColor = ovalColor,
        style = toqueTypography.headerPrimary
      )
      if (subtitle != null) {
        Spacer(modifier = Modifier.height(2.dp))
        TextOvalBackground(
          modifier = Modifier.padding(bottom = 2.dp),
          text = subtitle,
          color = contentColor,
          ovalColor = ovalColor,
          style = toqueTypography.headerSecondary
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      SongsItemsActions(
        modifier = Modifier.padding(bottom = 2.dp),
        itemCount = itemCount,
        selectedItems = selectedItems,
        viewModel = viewModel,
        buttonColors = buttonColors
      )
    },
    measurePolicy = BottomUpResizeHeightMeasurePolicy(
      heightSubtrahend,
      scrollConnection,
      screenConfig.preferredArtworkHeaderHeightPx
    )
  )
}
