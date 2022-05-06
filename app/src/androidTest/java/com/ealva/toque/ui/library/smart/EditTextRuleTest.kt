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

import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.TextMatcher
import com.nhaarman.expect.expect
import org.junit.Rule
import org.junit.Test

private const val testTag = "EditTextRule"

class EditTextRuleTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testEditTextRuleChangeText() {
    val text = "Title"
    val data = MatcherData(text, 0, 0)
    val textEditorRule = EditorRule.TextEditorRule(
      rule = com.ealva.toque.db.smart.Rule(1, RuleField.Comment, TextMatcher.Contains, data),
      editing = false,
      nameValidity = SmartPlaylistEditorViewModel.NameValidity.IsValid,
      suggestions = emptyList(),
      capitalization = Capitalization.Sentences
    )
    var changedRule: EditorRule? = null
    var changedData: MatcherData? = null
    composeTestRule.setContent {
      EditTextRule(
        editorRule = textEditorRule,
        ruleDataChanged = { editorRule, matcherData ->
          changedRule = editorRule
          changedData = matcherData
        }
      )
    }

    val editText = composeTestRule.onNode(
      matcher = hasParent(hasTestTag(testTag)) and hasText(data.text),
      useUnmergedTree = true
    )

    editText.assertExists()
    val replacement = "ZZ Top"
    editText.performTextClearance()
    editText.performTextReplacement(replacement)
    expect(changedRule).toNotBeNull()
    expect(changedData).toNotBeNull()
    expect(changedData?.text).toBe(replacement)
    expect(changedRule).toBe(textEditorRule)
  }

  @Test
  fun testEditTextRuleClearText() {
    val text = "Title"
    val data = MatcherData(text, 0, 0)
    val textEditorRule = EditorRule.TextEditorRule(
      rule = com.ealva.toque.db.smart.Rule(1, RuleField.Comment, TextMatcher.Contains, data),
      editing = false,
      nameValidity = SmartPlaylistEditorViewModel.NameValidity.IsValid,
      suggestions = emptyList(),
      capitalization = Capitalization.Sentences
    )
    var ruleDataChangedCount = 0
    var changedRule: EditorRule? = null
    var changedData: MatcherData? = null
    composeTestRule.setContent {
      EditTextRule(
        editorRule = textEditorRule,
        ruleDataChanged = { editorRule, matcherData ->
          ruleDataChangedCount++
          changedRule = editorRule
          changedData = matcherData
        }
      )
    }

    val editText = composeTestRule.onNode(
      matcher = hasParent(hasTestTag(testTag)) and hasText(data.text),
      useUnmergedTree = true
    )

    editText.assertExists()
    editText.performTextClearance()
    expect(ruleDataChangedCount).toBe(3)
    expect(changedRule).toNotBeNull()
    expect(changedData).toNotBeNull()
    expect(changedData?.text).toBe("")
    expect(changedRule).toBe(textEditorRule)
  }


  @Test
  fun testEditTextRuleBecomeFocused() {
    val text = "Title"
    val data = MatcherData(text, 0, 0)
    val textEditorRule = EditorRule.TextEditorRule(
      rule = com.ealva.toque.db.smart.Rule(1, RuleField.Comment, TextMatcher.Contains, data),
      editing = false,
      nameValidity = SmartPlaylistEditorViewModel.NameValidity.IsValid,
      suggestions = emptyList(),
      capitalization = Capitalization.Sentences
    )
    var ruleDataChangedCount = 0
    var changedRule: EditorRule? = null
    var changedData: MatcherData? = null
    composeTestRule.setContent {
      EditTextRule(
        editorRule = textEditorRule,
        ruleDataChanged = { editorRule, matcherData ->
          ruleDataChangedCount++
          changedRule = editorRule
          changedData = matcherData
          val rule = editorRule as EditorRule.TextEditorRule
          when (ruleDataChangedCount) {
            1 -> expect(rule.editing).toBe(false)
            2 -> expect(rule.editing).toBe(true)
            3 -> expect(rule.editing).toBe(false)
            else -> throw IllegalStateException("Unexpected call to ruleDataChanged lambda?")
          }
        }
      )
    }

    val editText = composeTestRule.onNode(
      matcher = hasParent(hasTestTag(testTag)) and hasText(data.text),
      useUnmergedTree = true
    )

    editText.assertExists()
    editText.performClick()
    expect(ruleDataChangedCount).toBe(2)
    expect(changedRule).toNotBeNull()
    expect(changedData).toNotBeNull()
    expect(changedData?.text).toBe(text)
    expect(changedRule).toBe(textEditorRule.copy(editing = true))
  }
}
