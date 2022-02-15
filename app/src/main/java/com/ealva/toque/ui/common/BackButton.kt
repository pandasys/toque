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

package com.ealva.toque.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography

@Composable
fun BackToButton(
  modifier: Modifier = Modifier,
  backTo: String,
  contentColor: Color = Color.White,
  ovalColor: Color = toqueColors.shadedBackground,
  back: () -> Unit
) {
  OvalBackground(modifier = modifier, ovalColor = ovalColor) {
    Row(
      modifier = Modifier
        .clickable { back() }
        .padding(end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_navigate_before),
        contentDescription = "Back",
        modifier = Modifier.size(26.dp),
        tint = contentColor
      )
      Text(
        text = backTo,
        color = contentColor,
        style = toqueTypography.subtitle2
      )
    }
  }
}
