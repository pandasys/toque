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
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp

private const val ALPHA_ENABLED = 1.0F
private const val ALPHA_DISABLED = 0.3F

@Composable
fun ActionButton(
  buttonHeight: Dp,
  modifier: Modifier,
  @DrawableRes drawable: Int,
  @StringRes description: Int,
  onClick: () -> Unit,
  enabled: Boolean = true
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
  ) {
    Icon(
      painter = painterResource(id = drawable),
      contentDescription = stringResource(id = description),
      modifier = Modifier.size(buttonHeight),
      tint = LocalContentColor.current.copy(alpha = if (enabled) ALPHA_ENABLED else ALPHA_DISABLED)
    )
  }
}
