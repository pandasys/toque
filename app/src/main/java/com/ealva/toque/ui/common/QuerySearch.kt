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

package com.ealva.toque.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.ealva.toque.ui.theme.toqueTypography

@Composable
fun QuerySearch(
  modifier: Modifier = Modifier,
  query: String,
  capitalization: KeyboardCapitalization,
  label: @Composable (() -> Unit)? = null,
  isError: Boolean = false,
  onDoneActionClick: () -> Unit = {},
  onClearClick: () -> Unit = {},
  onQueryChanged: (String) -> Unit,
  isFocused: (Boolean) -> Unit
) {
//  val focusRequester = remember { FocusRequester() }
//  val inputService = LocalTextInputService.current
  var showClearButton by remember { mutableStateOf(false) }
//  val focusManager = LocalFocusManager.current

  OutlinedTextField(
    modifier = modifier
//      .focusRequester(focusRequester)
      .fillMaxWidth()
      .onFocusChanged { focusState ->
        showClearButton = (focusState.isFocused)
        isFocused(focusState.isFocused)
      },
    value = query,
    onValueChange = { value -> onQueryChanged(value) },
    label = label,
    textStyle = toqueTypography.subtitle1,
    singleLine = true,
    trailingIcon = {
      if (showClearButton) {
        IconButton(onClick = { onClearClick() }) {
          Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear")
        }
      }
    },
    isError = isError,
    keyboardActions = KeyboardActions(onDone = {
      onDoneActionClick()
//      focusManager.moveFocus(FocusDirection.Next)
    }),
    keyboardOptions = KeyboardOptions(
      capitalization = capitalization,
      imeAction = ImeAction.Done,
      keyboardType = KeyboardType.Text
    )
  )
//
//  LaunchedEffect(Unit) {
////    inputService?.showSoftwareKeyboard()
//    delay(100)
//    focusRequester.requestFocus()
//  }
}
