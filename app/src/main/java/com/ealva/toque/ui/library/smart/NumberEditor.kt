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

package com.ealva.toque.ui.library.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.NumberMatcher
import com.ealva.toque.ui.library.smart.EditorRule.NumberEditorRule


@Composable
fun NumberEditor(editorRule: NumberEditorRule, ruleDataChanged: (EditorRule, MatcherData) -> Unit) {
  when (editorRule.rule.matcher as NumberMatcher) {
    NumberMatcher.IsInTheRange -> NumberRangeEditor(editorRule, ruleDataChanged)
    else -> SingleNumberEditor(editorRule, ruleDataChanged)
  }
}

@Composable
private fun SingleNumberEditor(
  editorRule: NumberEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  OutlinedTextField(
    value = editorRule.firstText,
    onValueChange = { value ->
      ruleDataChanged(
        editorRule.copy(firstText = value),
        editorRule.rule.data.copy(first = value.asLong)
      )
    },
    isError = editorRule.firstIsError,
    label = { Text(text = editorRule.firstLabel) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
  )
}

@Composable
private fun NumberRangeEditor(
  editorRule: NumberEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    OutlinedTextField(
      modifier = Modifier.weight(.35F),
      value = editorRule.firstText,
      onValueChange = { value ->
        ruleDataChanged(
          editorRule.copy(firstText = value),
          editorRule.rule.data.copy(first = value.asLong)
        )
      },
      isError = editorRule.firstIsError,
      label = { Text(text = editorRule.firstLabel) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Text(
      modifier = Modifier.padding(6.dp),
      text = stringResource(id = R.string.to)
    )
    OutlinedTextField(
      modifier = Modifier.weight(.35F),
      value = editorRule.secondText,
      onValueChange = { value ->
        ruleDataChanged(
          editorRule.copy(secondText = value),
          editorRule.rule.data.copy(second = value.asLong)
        )
      },
      isError = editorRule.secondIsError,
      label = { Text(text = editorRule.secondLabel) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
  }
}
