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

package com.ealva.toque.ui.library.search

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_BACK
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun SearchTextField(
  query: TextFieldValue,
  focusRequester: FocusRequester,
  onQueryTextChange: (TextFieldValue) -> Unit,
  onQueryTextSubmit: (TextFieldValue) -> Unit,
  onClearText: () -> Unit,
  onBackPressed: () -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  loading: Boolean = false,
) {
  Surface(
    elevation = 2.dp,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .focusRequester(focusRequester),
  ) {
    TextField(
      value = query,
      onValueChange = onQueryTextChange,
      singleLine = true,
      placeholder = { if (placeholder.isNotEmpty()) Text(text = placeholder) },
      colors = TextFieldDefaults.textFieldColors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        backgroundColor = Color.Transparent,
      ),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(
        onSearch = { onQueryTextSubmit(query) }
      ),
      leadingIcon = {
        IconButton(
          onClick = onBackPressed,
          content = { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back") },
        )
      },
      trailingIcon = {
        if (query.text.isNotEmpty())
          IconButton(
            onClick = { onClearText() },
            content = {
              if (loading) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colors.secondary,
                  modifier = Modifier.size(26.dp),
                )
              }

              Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
            },
          )
      },
      modifier = Modifier
        .onPreviewKeyEvent {
          val keyEvent = it.nativeKeyEvent
          if (keyEvent.action == ACTION_DOWN && keyEvent.keyCode == KEYCODE_BACK) {
            onBackPressed()
            true
          } else
            false
        },
    )
  }
}
