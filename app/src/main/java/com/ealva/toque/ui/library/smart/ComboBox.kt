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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> LabeledComboBox(
  modifier: Modifier = Modifier,
  value: T,
  possibleValues: List<T>,
  valueChanged: (T) -> Unit,
  @StringRes labelRes: Int,
  dropDownWidth: Dp = Dp.Unspecified
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.padding(end = 4.dp),
      text = stringResource(id = labelRes)
    )
    ComboBox(
      modifier = Modifier.width(dropDownWidth),
      value = value,
      possibleValues = possibleValues.toList(),
      valueChanged = valueChanged
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> ComboBox(
  modifier: Modifier = Modifier,
  value: T,
  possibleValues: List<T>,
  valueChanged: (T) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    modifier = modifier,
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
  ) {
    ComboBoxTextField(
      value = value.toString(),
      readOnly = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      possibleValues.toList().forEach { option ->
        DropdownMenuItem(
          onClick = {
            expanded = false
            valueChanged(option)
          }
        ) {
          Text(text = option.toString())
        }
      }
    }
  }
}

@Composable
private fun ComboBoxTextField(
  modifier: Modifier = Modifier,
  value: String,
  readOnly: Boolean,
  enabled: Boolean = true,
  textStyle: TextStyle = LocalTextStyle.current,
  trailingIcon: @Composable (() -> Unit)? = null
) {
  val colors: TextFieldColors = TextFieldDefaults.textFieldColors()
  val backgroundColor = colors.backgroundColor(enabled).value

  BasicTextField(
    modifier = modifier
      .background(backgroundColor, MaterialTheme.shapes.small)
      .padding(start = 6.dp),
    value = value,
    readOnly = readOnly,
    onValueChange = {},
    maxLines = 1,
    singleLine = true,
    textStyle = textStyle.copy(color = LocalContentColor.current),
    decorationBox = { innerTextField ->
      Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.weight(1f)) { innerTextField() }
        if (trailingIcon != null) trailingIcon()
      }
    }
  )
}
