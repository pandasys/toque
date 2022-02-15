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
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.NumberMatcher
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class NumberMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testIs() {
    val matcher = NumberMatcher.Is
    val clause = matcher.makeWhereClause(MediaTable.year, MatcherData("", 1960, 0))
    expect(clause.toString()).toBe("""Media.MediaYear = 1960""")
  }

  @Test
  fun testIsNot() {
    val matcher = NumberMatcher.IsNot
    val clause = matcher.makeWhereClause(MediaTable.year, MatcherData("", 1960, 0))
    expect(clause.toString()).toBe("""Media.MediaYear <> 1960""")
  }

  @Test
  fun testIsGreaterThan() {
    val matcher = NumberMatcher.IsGreaterThan
    val clause = matcher.makeWhereClause(MediaTable.year, MatcherData("", 1960, 0))
    expect(clause.toString()).toBe("""Media.MediaYear > 1960""")
  }

  @Test
  fun testIsLessThan() {
    val matcher = NumberMatcher.IsLessThan
    val clause = matcher.makeWhereClause(MediaTable.year, MatcherData("", 1960, 0))
    expect(clause.toString()).toBe("""Media.MediaYear < 1960""")
  }

  @Test
  fun testIsInTheRange() {
    val matcher = NumberMatcher.IsInTheRange
    val clause = matcher.makeWhereClause(MediaTable.year, MatcherData("", 1960, 1970))
    expect(clause.toString()).toBe("""Media.MediaYear BETWEEN 1960 AND 1970""")
  }

  @Test
  fun testWillAccept() {
    expect(NumberMatcher.Is.willAccept(MatcherData("", -1, 0))).toBe(false)
    expect(NumberMatcher.IsNot.willAccept(MatcherData("", -1, 0))).toBe(false)
    expect(NumberMatcher.IsGreaterThan.willAccept(MatcherData("", -1, 0))).toBe(false)
    expect(NumberMatcher.IsLessThan.willAccept(MatcherData("", -1, 0))).toBe(false)
    expect(NumberMatcher.IsInTheRange.willAccept(MatcherData("", -1, 0))).toBe(false)
    expect(NumberMatcher.IsInTheRange.willAccept(MatcherData("", 0, -1))).toBe(false)
    expect(NumberMatcher.IsInTheRange.willAccept(MatcherData("", 1970, 1969))).toBe(false)
  }

  @Test
  fun testFindById() {
    // IDs should never change
    listOf(
      1 to NumberMatcher.Is,
      2 to NumberMatcher.IsNot,
      3 to NumberMatcher.IsGreaterThan,
      4 to NumberMatcher.IsLessThan,
      5 to NumberMatcher.IsInTheRange,
    ).forEach { pair ->
      expect(NumberMatcher.fromId(pair.first)).toBe(pair.second)
    }
  }

  @Test
  fun testAllValues() {
    listOf(
      NumberMatcher.Is,
      NumberMatcher.IsNot,
      NumberMatcher.IsGreaterThan,
      NumberMatcher.IsLessThan,
      NumberMatcher.IsInTheRange,

      ).let { list ->
      expect(NumberMatcher.ALL_VALUES.size).toBe(list.size)
      list.forEach { matcher -> expect(NumberMatcher.ALL_VALUES).toContain(matcher) }
    }
  }
}
