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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.theme.toqueColors

@Composable
fun OvalBackground(
  modifier: Modifier = Modifier,
  ovalColor: Color = toqueColors.shadedBackground,
  content: @Composable () -> Unit
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.background(ovalColor, shape = RoundedCornerShape(50))
  ) {
    content()
  }
}

@Composable
fun TextOvalBackground(
  modifier: Modifier = Modifier,
  text: String,
  textAlign: TextAlign = TextAlign.Center,
  textPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
  maxLines: Int = Int.MAX_VALUE,
  style: TextStyle = LocalTextStyle.current,
  color: Color = LocalContentColor.current,
  ovalColor: Color = toqueColors.shadedBackground,
  ) {
  OvalBackground(modifier = modifier, ovalColor = ovalColor) {
    Text(
      text = text,
      textAlign = textAlign,
      maxLines = maxLines,
      style = style,
      color = color,
      modifier = Modifier
        .padding(paddingValues = textPadding)
        .defaultMinSize(24.dp) //Use a min size for short text.
    )
  }
}
