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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.ui.common.ToqueAlertDialog

@Composable
fun PlayUpNextPrompt(
  itemCount: Int,
  queueSize: Int,
  onDismiss: () -> Unit,
  onClear: () -> Unit,
  onDoNotClear: () -> Unit,
  onCancel: () -> Unit
) {
  ToqueAlertDialog(
    onDismissRequest = onDismiss,
    text = {
      Text(
        text = LocalContext.current.resources.getQuantityString(
          R.plurals.UpNextActionPrompt,
          itemCount,
          itemCount,
          queueSize,
        )
      )
    },
    buttons = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(all = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        Button(
          modifier = Modifier,
          onClick = onClear
        ) {
          Text("Clear")
        }
        Button(
          modifier = Modifier,
          onClick = onDoNotClear
        ) {
          Text("Add")
        }
        Button(
          modifier = Modifier,
          onClick = onCancel
        ) {
          Text("Cancel")
        }
      }
    }
  )
}
