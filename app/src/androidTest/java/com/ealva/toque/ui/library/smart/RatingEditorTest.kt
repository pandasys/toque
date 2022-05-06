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

package com.ealva.toque.ui.library.smart

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import com.ealva.toque.common.StarRating
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RatingMatcher
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.firstAsRating
import com.ealva.toque.db.smart.secondAsRating
import com.nhaarman.expect.expect
import org.junit.Rule
import org.junit.Test

class RatingEditorTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testSingleChangeRating() {
    val data = MatcherData(StarRating.STAR_0)
    val ratingEditorRule = EditorRule.RatingEditorRule(
      rule = com.ealva.toque.db.smart.Rule(1, RuleField.Rating, RatingMatcher.Is, data)
    )

    var ruleDataChangedCount = 0
    var changedRule: EditorRule? = null
    var changedData: MatcherData? = null
    composeTestRule.setContent {
      RatingEditor(
        editorRule = ratingEditorRule,
        ruleDataChanged = { editorRule, matcherData ->
          ruleDataChangedCount++
          changedRule = editorRule
          changedData = matcherData
        }
      )
    }

    val ratingBar = composeTestRule.onNode(
      matcher = hasTestTag("RatingBar"),
      useUnmergedTree = true
    )

    ratingBar.assertExists()
    ratingBar.performTouchInput {
      click()
    }
    expect(ruleDataChangedCount).toBe(1)
    expect(changedRule).toNotBeNull()
    expect(changedData).toNotBeNull()
    expect(changedData?.firstAsRating).toBe(StarRating.STAR_3)
  }


  @Test
  fun testRangeChangeRating() {
    val data = MatcherData(StarRating.STAR_0, StarRating.STAR_2)
    val ratingEditorRule = EditorRule.RatingEditorRule(
      com.ealva.toque.db.smart.Rule(1, RuleField.Rating, RatingMatcher.IsInTheRange, data)
    )

    var ruleDataChangedCount = 0
    var changedRule: EditorRule? = null
    var changedData: MatcherData? = null
    composeTestRule.setContent {
      RatingEditor(
        editorRule = ratingEditorRule,
        ruleDataChanged = { editorRule, matcherData ->
          ruleDataChangedCount++
          changedRule = editorRule
          changedData = matcherData
        }
      )
    }

    val ratingBarLow = composeTestRule.onNode(
      matcher = hasTestTag("RatingBarLow"),
      useUnmergedTree = true
    )
    val ratingBarHigh = composeTestRule.onNode(
      matcher = hasTestTag("RatingBarHigh"),
      useUnmergedTree = true
    )

    ratingBarLow.assertExists()
    ratingBarHigh.assertExists()

    // Touch center of low rating sets to 3 and raises high rating to 3.5
    ratingBarLow.performTouchInput {
      click(position = center)
    }
    expect(ruleDataChangedCount).toBe(1)
    expect(changedRule).toNotBeNull()
    expect(changedData).toNotBeNull()
    expect(changedData?.firstAsRating).toBe(StarRating.STAR_3)
    expect(changedData?.secondAsRating).toBe(StarRating.STAR_3_5)

    // Touch center of high rating sets to 3 and lowers low rating to 2.5
    ratingBarHigh.performTouchInput {
      click(position = center)
    }
    expect(ruleDataChangedCount).toBe(2)
    expect(changedData?.firstAsRating).toBe(StarRating.STAR_2_5)
    expect(changedData?.secondAsRating).toBe(StarRating.STAR_3)
  }
}
