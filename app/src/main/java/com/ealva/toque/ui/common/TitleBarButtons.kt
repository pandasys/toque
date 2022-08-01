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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.ui.icons.filled.ArrowBack

@Composable
fun TitleBarIconButton(
  onClick: () -> Unit,
  imageVector: ImageVector,
  contentDescription: String,
  tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
  IconButton(
    onClick = onClick,
    content = {
      Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint
      )
    },
  )
}

@Composable
fun BackButton(onClick: () -> Unit) {
  TitleBarIconButton(
    onClick = onClick,
    imageVector = ArrowBack,
    contentDescription = fetch(R.string.GoBack)
  )
}

//@Composable
//fun SaveAsButton(onClick: () -> Unit) {
//  TitleBarIconButton(
//    onClick = onClick,
//    imageVector = SaveAlt,
//    contentDescription = fetch(R.string.SaveAs)
//  )
//}

@Composable
fun AssignButton(onClick: () -> Unit) {
  TitleBarButton(
    iconRes = R.drawable.ic_clipboard_text,
    contentDescription = fetch(R.string.SaveAs),
    onClick = onClick,
  )
}

@Composable
fun TitleBarButton(
  @DrawableRes iconRes: Int,
  contentDescription: String,
  iconSize: Dp = 28.dp,
  tint: Color = LocalContentColor.current,
  onClick: () -> Unit
) {
  IconButton(onClick = onClick) {
    Icon(
      modifier = Modifier.size(iconSize),
      painter = painterResource(id = iconRes),
      contentDescription = contentDescription,
      tint = tint
    )
  }
}

@Composable
fun SearchButton(onClick: () -> Unit) {
  TitleBarButton(
    onClick = onClick,
    iconRes = R.drawable.ic_search,
    contentDescription = fetch(R.string.Search)
  )
}

@Composable
fun ResetButton(onClick: () -> Unit) {
  TitleBarButton(
    onClick = onClick,
    iconRes = R.drawable.ic_restore,
    contentDescription = fetch(R.string.Reset)
  )
}
