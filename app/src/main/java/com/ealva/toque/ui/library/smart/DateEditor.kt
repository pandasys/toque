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

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.asMediumDate
import com.ealva.toque.common.fetch
import com.ealva.toque.db.smart.DateMatcher
import com.ealva.toque.db.smart.DateMatcher.InTheLast
import com.ealva.toque.db.smart.DateMatcher.Is
import com.ealva.toque.db.smart.DateMatcher.IsAfter
import com.ealva.toque.db.smart.DateMatcher.IsBefore
import com.ealva.toque.db.smart.DateMatcher.IsInTheRange
import com.ealva.toque.db.smart.DateMatcher.IsNot
import com.ealva.toque.db.smart.DateMatcher.NotInTheLast
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.TheLast
import com.ealva.toque.ui.library.smart.EditorRule.DateEditorRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun DateEditor(
  editorRule: DateEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  when (editorRule.rule.matcher as DateMatcher) {
    IsInTheRange -> RangeDateEditor(editorRule, ruleDataChanged)
    InTheLast, NotInTheLast -> InTheLastDateEditor(editorRule, ruleDataChanged)
    Is, IsNot, IsAfter, IsBefore -> SingleDateEditor(editorRule, ruleDataChanged)
  }
}

@Composable
private fun SingleDateEditor(
  editorRule: DateEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val data = editorRule.rule.data
  val context = LocalContext.current
  val date = Instant.ofEpochMilli(data.first).atZone(ZoneId.systemDefault()).toLocalDate()
  val startYear = date.year
  val startMonth = date.monthValue - 1
  val startDay = date.dayOfMonth

  OutlinedTextField(
    modifier = Modifier
      .fillMaxWidth()
      .clickable {
        DatePickerDialog(
          context,
          { _, year, month, day ->
            val epochMilli = LocalDate
              .of(year, month + 1, day)
              .atStartOfDay()
              .atOffset(ZoneOffset.UTC)
              .toInstant()
              .toEpochMilli()
            ruleDataChanged(editorRule, data.copy(first = epochMilli))
          },
          startYear,
          startMonth,
          startDay
        ).show()
      },
    value = Millis(data.first).asMediumDate,
    enabled = false,
    readOnly = true,
    onValueChange = {}
  )
}

@Composable
private fun RangeDateEditor(
  editorRule: DateEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val data = editorRule.rule.data
  val context = LocalContext.current
  val firstDate = Instant.ofEpochMilli(data.first).atZone(ZoneId.systemDefault()).toLocalDate()
  val startYear = firstDate.year
  val startMonth = firstDate.monthValue - 1
  val startDay = firstDate.dayOfMonth

  val secondDate = Instant.ofEpochMilli(data.second).atZone(ZoneId.systemDefault()).toLocalDate()
  val endYear = secondDate.year
  val endMonth = secondDate.monthValue - 1
  val endDay = secondDate.dayOfMonth

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    OutlinedTextField(
      modifier = Modifier
        .weight(.35F)
        .clickable {
          DatePickerDialog(
            context,
            { _, year, month, day ->
              val date = LocalDate.of(year, month + 1, day)
              val epochMilli = date
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
              val secondMillis = if (date >= secondDate) {
                date
                  .plusDays(1)
                  .atStartOfDay()
                  .atOffset(ZoneOffset.UTC)
                  .toInstant()
                  .toEpochMilli()
              } else data.second
              ruleDataChanged(editorRule, data.copy(first = epochMilli, second = secondMillis))
            },
            startYear,
            startMonth,
            startDay
          ).show()
        },
      value = Millis(data.first).asMediumDate,
      enabled = false,
      readOnly = true,
      onValueChange = {}
    )
    Text(
      modifier = Modifier.padding(horizontal = 6.dp),
      text = stringResource(id = R.string.to)
    )
    OutlinedTextField(
      modifier = Modifier
        .weight(.35F)
        .clickable {
          DatePickerDialog(
            context,
            { _, year, month, day ->
              val date = LocalDate.of(year, month + 1, day)
              val epochMilli = date
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
              val firstMillis = if (date <= firstDate) {
                date
                  .minusDays(1)
                  .atStartOfDay()
                  .atOffset(ZoneOffset.UTC)
                  .toInstant()
                  .toEpochMilli()
              } else data.first
              ruleDataChanged(editorRule, data.copy(first = firstMillis, second = epochMilli))
            },
            endYear,
            endMonth,
            endDay
          ).show()
        },
      value = Millis(data.second).asMediumDate,
      enabled = false,
      readOnly = true,
      onValueChange = {}
    )
  }
}

@Composable
private fun InTheLastDateEditor(
  editorRule: DateEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val data = editorRule.rule.data
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextField(
      modifier = Modifier
        .padding(start = 2.dp)
        .weight(.3F),
      value = editorRule.textValue,
      maxLines = 1,
      singleLine = true,
      label = if (data.first <= 0) {
        { Text(text = fetch(R.string.MustBeGreaterThanX, 0)) }
      } else null,
      onValueChange = { value ->
        val first = if (value.isNotBlank() && value.isDigitsOnly()) value.toLong() else 0
        ruleDataChanged(editorRule.copy(textValue = value), data.copy(first = first))
      },
      isError = data.first <= 0,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    ComboBox(
      modifier = Modifier.weight(.4F),
      value = TheLast.fromId(data.second.toInt(), TheLast.Days),
      possibleValues = TheLast.ALL_VALUES,
      valueChanged = { theLast ->
        ruleDataChanged(editorRule, data.copy(second = theLast.id.toLong()))
      }
    )
  }
}
