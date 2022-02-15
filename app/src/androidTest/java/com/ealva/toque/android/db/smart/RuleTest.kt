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

package com.ealva.toque.android.db.smart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.smart.DateMatcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.PlaylistMatcher
import com.ealva.toque.db.smart.Rule
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.TextMatcher
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RuleTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test(expected = IllegalArgumentException::class)
  fun testRuleIllegalArg() {
    Rule(1, RuleField.Title, DateMatcher.NotInTheLast, MatcherData("", 0L, 0L))
  }

  @Test(expected = IllegalArgumentException::class)
  fun testSecondaryCtorIllegalArg() {
    Rule(1, RuleField.Title, Int.MIN_VALUE, MatcherData("", 0L, 0L))
  }

  @Test
  fun testMakeWhereClause() {
    val rule = Rule(1, RuleField.Title, TextMatcher.Is, MatcherData("Value", 0L, 0L))
    expect(rule.makeWhereClause().toString()).toBe("""Media.MediaTitle = 'Value'""")
    expect(rule.makeJoinTemplate()?.toString()).toBeNull()
  }

  @Test
  fun testMakeJoinClause() {
    val rule = Rule(
      1,
      RuleField.Playlist,
      PlaylistMatcher.Is,
      MatcherData("TheView", PlayListType.Rules.id.toLong(), 10L)
    )
    expect(rule.makeWhereClause().toString()).toBe("""TheView.MediaId IS NOT NULL""")
    val template = rule.makeJoinTemplate()
    expect(template).toNotBeNull { "template was null" }
    expect(template?.joinTo(MediaTable)?.toString())
      .toBe("""Media LEFT JOIN TheView ON Media.MediaId = TheView.MediaId""")
  }
}
