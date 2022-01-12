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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.toRating
import com.ealva.toque.common.toStarRating
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RatingMatcher
import com.ealva.toque.ui.theme.toqueTypography
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize


@Composable
fun RatingEditor(editorRule: EditorRule, ruleDataChanged: (EditorRule, MatcherData) -> Unit) {
  when (editorRule.rule.matcher as RatingMatcher) {
    RatingMatcher.IsInTheRange -> RatingRangeEditor(editorRule, ruleDataChanged)
    else -> SingleRatingEditor(editorRule, ruleDataChanged)
  }
}

@Composable
private fun SingleRatingEditor(
  editorRule: EditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = editorRule.firstStarRating.value,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {
        ruleDataChanged(
          editorRule,
          editorRule.rule.data.copy(first = StarRating(it).toRating().value.toLong())
        )
      },
      onRatingChanged = {
      },
    )
  }
}

@Composable
private fun RatingRangeEditor(
  editorRule: EditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  var low: Float by remember { mutableStateOf(editorRule.firstStarRating.value) }
  var high: Float by remember { mutableStateOf(editorRule.secondStarRating.value) }

  fun lowChanged(newLow: Float) {
    if (newLow >= high) {
      if (newLow == StarRating.STAR_5.value) {
        high = StarRating.STAR_5.value
        low = StarRating.STAR_4_5.value
      } else {
        low = newLow
        high = newLow + StarRating.STAR_0_5.value
      }
    } else {
      low = newLow
    }
  }

  fun highChanged(newHigh: Float) {
    if (low >= newHigh) {
      if (newHigh == StarRating.STAR_0.value) {
        low = StarRating.STAR_0.value
        high = StarRating.STAR_0_5.value
      } else {
        high = newHigh
        low = newHigh - StarRating.STAR_0_5.value
      }
    } else {
      high = newHigh
    }
  }

  fun ratingChanged(rule: EditorRule) {
    ruleDataChanged(
      rule,
      MatcherData(
        "",
        StarRating(low).toRating().value.toLong(),
        StarRating(high).toRating().value.toLong()
      )
    )
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = low,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = { lowChanged(it) },
      onRatingChanged = { ratingChanged(editorRule) },
    )
    Text(
      modifier = Modifier.padding(horizontal = 8.dp),
      text = stringResource(id = R.string.to),
      style = toqueTypography.caption
    )
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = high,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = { highChanged(it) },
      onRatingChanged = { ratingChanged(editorRule) },
    )
  }
}

private val EditorRule.firstStarRating: StarRating
  get() = Rating(rule.data.first.toInt()).toStarRating()

private val EditorRule.secondStarRating: StarRating
  get() = Rating(rule.data.second.toInt()).toStarRating()
