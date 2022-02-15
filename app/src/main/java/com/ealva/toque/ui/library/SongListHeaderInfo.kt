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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ealva.toque.persist.MediaId
import com.ealva.toque.ui.common.BackToButton
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.TextOvalBackground
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
  back: () -> Unit
) {
  val inPortrait = LocalScreenConfig.current.inPortrait
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 14.dp)
  ) {
    val contentColor = buttonColors.contentColor(enabled = true).value
    val notInSelectionMode = !selectedItems.inSelectionMode
    if (notInSelectionMode) {
      Spacer(modifier = Modifier.height(2.dp))
      BackToButton(
        modifier = Modifier,
        backTo = backTo,
        back = back
      )
      Spacer(modifier = Modifier.height(if (inPortrait) 28.dp else 13.dp))
    }
    Spacer(
      modifier = Modifier
        .height(2.dp)
        .weight(1F)
    )
    TextOvalBackground(
      modifier = Modifier.padding(vertical = 2.dp),
      text = title,
      color = contentColor,
      style = toqueTypography.headerPrimary
    )
    if (subtitle != null) {
      TextOvalBackground(
        modifier = Modifier.padding(bottom = 2.dp),
        text = subtitle,
        color = contentColor,
        style = toqueTypography.headerSecondary
      )
    }
    SongsItemsActions(
      modifier = Modifier.padding(bottom = 2.dp),
      itemCount = itemCount,
      selectedItems = selectedItems,
      viewModel = viewModel,
      buttonColors = buttonColors
    )
  }
}
