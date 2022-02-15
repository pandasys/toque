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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.ui.theme.toqueColors

@Composable
fun ActionButton(
  modifier: Modifier,
  iconSize: Dp,
  @DrawableRes drawable: Int,
  @StringRes description: Int,
  enabled: Boolean = true,
  colors: ButtonColors,
  onClick: () -> Unit
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    border = null,
    contentPadding = PaddingValues(0.dp),
    colors = colors,
    modifier = modifier
  ) {
    Icon(
      painter = painterResource(id = drawable),
      contentDescription = stringResource(id = description),
      modifier = Modifier.size(iconSize),
      tint = colors.contentColor(enabled = enabled).value
    )
  }
}

object ActionButtonDefaults {
  @Composable
  fun overArtworkColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    backgroundColor = toqueColors.shadedBackground,
    contentColor = Color.White,
    disabledContentColor = Color.White.copy(alpha = ContentAlpha.disabled)
  )

  @Composable
  fun colors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    backgroundColor = Color.Transparent,
    contentColor = LocalContentColor.current,
    disabledContentColor = LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
  )
}

