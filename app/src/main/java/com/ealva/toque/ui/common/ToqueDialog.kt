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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ToqueDialog(
  onDismissRequest: () -> Unit,
  shape: Shape = RoundedCornerShape(10),
  elevation: Dp = 1.dp,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = properties
  ) {
    Surface(
      modifier = Modifier.padding(8.dp),
      shape = shape,
      elevation = elevation,
      content = content
    )
  }
}

@Composable
fun ToqueAlertDialog(
  onDismissRequest: () -> Unit,
  buttons: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  title: (@Composable () -> Unit)? = null,
  text: @Composable (() -> Unit)? = null,
  elevation: Dp = 1.dp,
  shape: Shape = MaterialTheme.shapes.medium,
  color: Color = MaterialTheme.colors.surface,
  contentColor: Color = contentColorFor(color),
  properties: DialogProperties = DialogProperties()
) {
  val elevationOverlay = LocalElevationOverlay.current
  val absoluteElevation = LocalAbsoluteElevation.current + elevation
  val backgroundColor = if (color == MaterialTheme.colors.surface && elevationOverlay != null) {
    elevationOverlay.apply(color, absoluteElevation)
  } else {
    color
  }
  AlertDialog(
    onDismissRequest,
    buttons,
    modifier,
    title,
    text,
    shape,
    backgroundColor,
    contentColor,
    properties
  )
}
