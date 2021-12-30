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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.db.smart.DurationMatcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.ui.common.ListItemPicker
import java.util.concurrent.TimeUnit

@JvmInline
value class Hours(val value: Int) : Comparable<Hours> {
  val asMillis: Millis get() = Millis(TimeUnit.HOURS.toMillis(value.toLong()))

  operator fun minus(rhs: Int): Hours = Hours(value - rhs)
  operator fun plus(rhs: Int): Hours = Hours(value + rhs)

  override fun compareTo(other: Hours): Int = value.compareTo(other.value)
}

@JvmInline
value class Minutes(val value: Int) : Comparable<Minutes> {
  val asMillis: Millis get() = Millis(TimeUnit.MINUTES.toMillis(value.toLong()))

  operator fun minus(rhs: Int): Minutes = Minutes(value - rhs)
  operator fun plus(rhs: Int): Minutes = Minutes(value + rhs)

  override fun compareTo(other: Minutes): Int = value.compareTo(other.value)

  operator fun compareTo(range: ClosedRange<Minutes>): Int {
    return when {
      this < range.start -> -1
      this > range.endInclusive -> 1
      else -> 0
    }
  }

  companion object {
    val WITHIN_HOUR: ClosedRange<Minutes> = Minutes(0)..Minutes(59)
  }
}

@JvmInline
value class Seconds(val value: Int) : Comparable<Seconds> {
  val asMillis: Millis get() = Millis(TimeUnit.SECONDS.toMillis(value.toLong()))

  operator fun minus(rhs: Int): Seconds = Seconds(value - rhs)
  operator fun plus(rhs: Int): Seconds = Seconds(value + rhs)

  override fun compareTo(other: Seconds): Int = value.compareTo(other.value)

  operator fun compareTo(range: ClosedRange<Seconds>): Int {
    return when {
      this < range.start -> -1
      this > range.endInclusive -> 1
      else -> 0
    }
  }

  companion object {
    val WITHIN_MINUTE = Seconds(0)..Seconds(59)
  }
}

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
      millis = Millis(data.first),
      timeUpdated = { millis -> ruleDataChanged(editorRule, data.copy(first = millis.value)) }
    )
  }
}

@Composable
fun DurationRangeEditor(
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
      millis = Millis(data.first),
      timeUpdated = { millis -> ruleDataChanged(editorRule, data.copy(first = millis.value)) }
    )
    Text(
      modifier = Modifier.padding(horizontal = 4.dp),
      text = stringResource(id = R.string.to)
    )
    HoursMinutesSecondsEditor(
      millis = Millis(data.second),
      timeUpdated = { millis -> ruleDataChanged(editorRule, data.copy(second = millis.value)) }
    )
  }
}

@Composable
private fun HoursMinutesSecondsEditor(millis: Millis, timeUpdated: (Millis) -> Unit) {
  val seconds = millis.seconds
  val minutes = millis.minutes
  val hours = millis.hours

  LabeledNumberPicker(
    value = hours.value,
    label = stringResource(R.string.h_hours),
    range = 0..99,
    onValueChange = { newHours -> timeUpdated(timeChanged(Hours(newHours), minutes, seconds)) }
  )
  LabeledNumberPicker(
    value = minutes.value,
    valueText = ::secondsOrMinutesLabel,
    label = stringResource(R.string.m_minutes),
    range = -1..60,
    onValueChange = { newMinutes -> timeUpdated(timeChanged(hours, Minutes(newMinutes), seconds)) }
  )
  LabeledNumberPicker(
    value = seconds.value,
    valueText = ::secondsOrMinutesLabel,
    label = stringResource(R.string.s_seconds),
    range = -1..60,
    onValueChange = { newSeconds -> timeUpdated(timeChanged(hours, minutes, Seconds(newSeconds))) }
  )
}

fun secondsOrMinutesLabel(value: Int): String = when (value) {
  -1, 60 -> ""
  else -> value.toString()
}

private val HOURS_RANGE = Hours(0)..Hours(99)

private fun timeChanged(newHours: Hours, newMinutes: Minutes, newSeconds: Seconds): Millis {
  var hours = newHours
  var minutes = newMinutes
  var seconds = newSeconds
  when {
    seconds < Seconds.WITHIN_MINUTE -> {
      seconds = Seconds.WITHIN_MINUTE.endInclusive
      minutes -= 1
    }
    seconds > Seconds.WITHIN_MINUTE -> {
      seconds = Seconds.WITHIN_MINUTE.start
      minutes += 1
    }
  }
  when {
    minutes < Minutes.WITHIN_HOUR -> {
      minutes = Minutes.WITHIN_HOUR.endInclusive
      hours -= 1
    }
    minutes > Minutes.WITHIN_HOUR -> {
      minutes = Minutes.WITHIN_HOUR.start
      hours += 1
    }
  }

  hours = hours.coerceIn(HOURS_RANGE)
  return toMillis(hours, minutes, seconds)
}

@Composable
private fun LabeledNumberPicker(
  modifier: Modifier = Modifier,
  valueText: (Int) -> String = { it.toString() },
  value: Int,
  label: String,
  onValueChange: (Int) -> Unit,
  dividersColor: Color = MaterialTheme.colors.primary,
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

private fun toMillis(hours: Hours, minutes: Minutes, seconds: Seconds): Millis =
  hours.asMillis + minutes.asMillis + seconds.asMillis

private val Millis.hours: Hours get() = Hours((value / (1000 * 60 * 60) % 24).toInt())
private val Millis.minutes: Minutes get() = Minutes((value / (1000 * 60) % 60).toInt())
private val Millis.seconds: Seconds get() = Seconds((value / 1000).toInt() % 60)
