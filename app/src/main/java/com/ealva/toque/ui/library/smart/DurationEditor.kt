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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.chargemap.compose.numberpicker.ListItemPicker
import com.ealva.toque.R
import com.ealva.toque.db.smart.DurationMatcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.ui.theme.toqueColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun DurationEditor(
  editorRule: EditorRule.DurationRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  when (editorRule.rule.matcher as DurationMatcher) {
    DurationMatcher.IsInTheRange -> DurationRangeEditor(editorRule, ruleDataChanged)
    else -> SingleDurationEditor(editorRule, ruleDataChanged)
  }
}

@Composable
private fun SingleDurationEditor(
  editorRule: EditorRule.DurationRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val data = editorRule.rule.data

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    HoursMinutesSecondsEditor(
      data.first.milliseconds,
      timeUpdated = { duration ->
        ruleDataChanged(
          editorRule,
          data.copy(first = duration.coerceAtLeast(ZERO).inWholeMilliseconds))
      }
    )
  }
}

@Composable
private fun DurationRangeEditor(
  editorRule: EditorRule.DurationRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val data = editorRule.rule.data

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    HoursMinutesSecondsEditor(
      data.first.milliseconds,
      timeUpdated = { duration ->
        val first = duration.coerceAtLeast(ZERO)
        ruleDataChanged(
          editorRule,
          data.copy(
            first = first.inWholeMilliseconds,
            second = data.second.milliseconds.coerceAtLeast(first + 1.seconds).inWholeMilliseconds
          )
        )
      }
    )
    Text(
      modifier = Modifier.padding(horizontal = 4.dp),
      text = stringResource(id = R.string.to)
    )
    HoursMinutesSecondsEditor(
      data.second.milliseconds,
      timeUpdated = { duration ->
        val first = data.first.milliseconds
          .coerceAtMost(duration - 1.seconds)
          .coerceAtLeast(ZERO)
        ruleDataChanged(
          editorRule,
          data.copy(
            first = first.inWholeMilliseconds,
            second = duration.coerceAtLeast(first + 1.seconds).inWholeMilliseconds
          )
        )
      }
    )
  }
}

@Composable
private fun HoursMinutesSecondsEditor(duration: Duration, timeUpdated: (Duration) -> Unit) {
  duration.components { hours, minutes, seconds ->
    LabeledNumberPicker(
      value = hours.value.toInt(),
      label = stringResource(R.string.h_hours),
      range = 0..99,
      onValueChange = { newHours -> timeUpdated(toDuration(Hours(newHours), minutes, seconds)) }
    )
    LabeledNumberPicker(
      value = minutes.value,
      valueText = ::secondsOrMinutesLabel,
      label = stringResource(R.string.m_minutes),
      range = -1..60,
      onValueChange = { newMinutes -> timeUpdated(toDuration(hours, Minutes(newMinutes), seconds)) }
    )
    LabeledNumberPicker(
      value = seconds.value,
      valueText = ::secondsOrMinutesLabel,
      label = stringResource(R.string.s_seconds),
      range = -1..60,
      onValueChange = { newSeconds -> timeUpdated(toDuration(hours, minutes, Seconds(newSeconds))) }
    )
  }
}

private fun secondsOrMinutesLabel(value: Int): String = when (value) {
  -1, 60 -> ""
  else -> value.toString()
}

private fun toDuration(hours: Hours, minutes: Minutes, seconds: Seconds): Duration =
  hours.asDuration + minutes.asDuration + seconds.asDuration

@Composable
private fun LabeledNumberPicker(
  modifier: Modifier = Modifier,
  valueText: (Int) -> String = { it.toString() },
  value: Int,
  label: String,
  onValueChange: (Int) -> Unit,
  dividersColor: Color = toqueColors.primary,
  range: IntRange,
  textStyle: TextStyle = LocalTextStyle.current,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ListItemPicker(
      modifier,
      valueText,
      value,
      onValueChange,
      dividersColor,
      range.toList(),
      textStyle
    )
    Text(text = label)
  }
}

@JvmInline
value class Hours(val value: Long) {
  inline val asDuration: Duration get() = value.hours

  companion object {
    operator fun invoke(intValue: Int): Hours = Hours(intValue.toLong())
  }
}

@JvmInline
private value class Minutes(val value: Int) {
  inline val asDuration: Duration get() = value.minutes
}

@JvmInline
value class Seconds(val value: Int) {
  inline val asDuration: Duration get() = value.seconds
}

private inline fun <T> Duration.components(
  action: (hours: Hours, minutes: Minutes, seconds: Seconds) -> T
): T {
  return toComponents { hours, minutes, seconds, _ ->
    action(Hours(hours), Minutes(minutes), Seconds(seconds))
  }
}
