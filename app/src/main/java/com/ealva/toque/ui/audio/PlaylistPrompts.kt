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

package com.ealva.toque.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ealva.toque.R
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.db.PlaylistIdNameType

@Composable
fun SelectPlaylistPrompt(
  playlists: List<PlaylistIdNameType>,
  onDismiss: () -> Unit,
  listSelected: (PlaylistIdNameType) -> Unit,
  createPlaylist: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    buttons = {},
    text = {
      LazyColumn {
        items(items = playlists, key = { it.id }) { idNameType ->
          Text(
            text = idNameType.name.value,
            modifier = Modifier
              .clickable { listSelected(idNameType) }
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .defaultMinSize(minWidth = 200.dp)
          )
        }
        item("Key") {
          Text(
            text = stringResource(id = R.string.PlusNewPlaylist),
            modifier = Modifier
              .clickable(onClick = createPlaylist)
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .defaultMinSize(minWidth = 200.dp)
          )
        }
      }
    }
  )
}

@Composable
fun CreatePlaylistPrompt(
  suggestedName: String,
  checkValidName: (PlaylistName) -> Boolean,
  dismiss: () -> Unit,
  createPlaylist: (PlaylistName) -> Unit,
) {
  var name by remember { mutableStateOf(suggestedName) }
  var nameIsValid by remember { mutableStateOf(true) }

  fun nameValidity(newName: String) {
    nameIsValid = checkValidName(newName.asPlaylistName)
    name = newName
  }

  AlertDialog(
    onDismissRequest = dismiss,
    buttons = {
      Row(
        modifier = Modifier
          .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
          .fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        Button(
          modifier = Modifier.padding(start = 8.dp),
          onClick = dismiss
        ) {
          Text(stringResource(R.string.Cancel))
        }
        Button(
          modifier = Modifier.padding(horizontal = 8.dp),
          enabled = nameIsValid,
          onClick = { createPlaylist(name.asPlaylistName) }
        ) {
          Text(stringResource(R.string.OK))
        }
      }
    },
    text = {
      OutlinedTextField(
        modifier = Modifier.defaultMinSize(minWidth = 200.dp),
        value = name,
        onValueChange = { nameValidity(it) },
        label = { Text(text = stringResource(R.string.NewPlaylist)) },
        singleLine = true,
      )
    }
  )
}
