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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e

private val LOG by lazyLogger("AutoCompleteView")

@Composable
fun <T> AutoCompleteView(
  modifier: Modifier,
  predictions: List<T>,
  onItemClick: (T) -> Unit,
  itemContent: @Composable (T) -> Unit,
  querySearch: @Composable () -> Unit,
) {
  val view = LocalView.current
  val lazyListState = rememberLazyListState()
  Column(
    modifier = modifier
  ) {
    querySearch()
    LazyColumn(
      state = lazyListState,
      modifier = Modifier.heightIn(max = TextFieldDefaults.MinHeight * 4)
    ) {
      if (predictions.count() > 0) {
        items(predictions) { prediction ->
          Row(
            Modifier
              .padding(8.dp)
              .fillMaxWidth()
              .clickable {
                view.clearFocus()
                onItemClick(prediction)
              }
          ) {
            itemContent(prediction)
          }
        }
      }
    }
  }
}

@Composable
fun AutoCompleteTextView(
  modifier: Modifier = Modifier,
  query: String,
  suggestions: List<String>,
  isError: Boolean,
  onTextChanged: (String) -> Unit,
  onSelected: (String) -> Unit,
  onDoneActionClick: () -> Unit,
  isFocused: (Boolean) -> Unit,
  label: @Composable() (() -> Unit)? = null,
) {
  AutoCompleteView(
    modifier = modifier,
    predictions = suggestions,
    onItemClick = { selectedText -> onSelected(selectedText) },
    itemContent = { Text(it) }
  ) {
    QuerySearch(
      modifier = Modifier.fillMaxWidth(),
      query = query,
      label = label,
      isError = isError,
      onQueryChanged = onTextChanged,
      onDoneActionClick = onDoneActionClick,
      onClearClick = { onTextChanged("") },
      isFocused = isFocused
    )
  }
}
