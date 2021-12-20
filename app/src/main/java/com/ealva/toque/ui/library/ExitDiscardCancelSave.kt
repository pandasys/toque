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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExitDiscardCancelSave(
  isSavable: Boolean,
  onCancel: () -> Unit,
  onSave: () -> Unit,
  onExit: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onCancel,
    text = {
      Text(text = "Exit and discard all changes?")
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
          onClick = onExit
        ) {
          Text("Exit")
        }
        Button(
          modifier = Modifier,
          onClick = onCancel
        ) {
          Text("Cancel")
        }
        if (isSavable) {
          Button(
            modifier = Modifier,
            onClick = onSave
          ) {
            Text("Save")
          }
        }
      }
    }
  )
}
