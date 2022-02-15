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
import com.ealva.toque.common.StarRating
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RatingMatcher
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RatingMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testIs() {
    val matcher = RatingMatcher.Is
    val clause = matcher.makeWhereClause(MediaTable.rating, MatcherData(StarRating.STAR_NONE))
    expect(clause.toString()).toBe("""Media.MediaRating = -1""")
  }

  @Test
  fun testIsNot() {
    val matcher = RatingMatcher.IsNot
    val clause = matcher.makeWhereClause(MediaTable.rating, MatcherData(StarRating.STAR_5))
    expect(clause.toString()).toBe("""Media.MediaRating <> 100""")
  }

  @Test
  fun testIsGreaterThan() {
    val matcher = RatingMatcher.IsGreaterThan
    val clause = matcher.makeWhereClause(MediaTable.rating, MatcherData(StarRating.STAR_4))
    expect(clause.toString()).toBe("""Media.MediaRating > 80""")
  }

  @Test
  fun testIsLessThan() {
    val matcher = RatingMatcher.IsLessThan
    val clause = matcher.makeWhereClause(MediaTable.rating, MatcherData(StarRating.STAR_2))
    expect(clause.toString()).toBe("""Media.MediaRating < 40""")
  }

  @Test
  fun testIsInTheRange() {
    val matcher = RatingMatcher.IsInTheRange
    val clause =
      matcher.makeWhereClause(MediaTable.rating, MatcherData(StarRating.STAR_0, StarRating.STAR_3))
    expect(clause.toString()).toBe("""Media.MediaRating BETWEEN 0 AND 60""")
  }

  @Test
  fun testWillAccept() {
    expect(RatingMatcher.Is.willAccept(MatcherData("", 101, 0))).toBe(false)
    expect(RatingMatcher.IsNot.willAccept(MatcherData("", -2, 0))).toBe(false)
    expect(RatingMatcher.IsGreaterThan.willAccept(MatcherData("", 256, 0))).toBe(false)
    expect(RatingMatcher.IsLessThan.willAccept(MatcherData("", 500, 0))).toBe(false)
    expect(RatingMatcher.IsInTheRange.willAccept(MatcherData("", -1, 101))).toBe(false)
    expect(RatingMatcher.IsInTheRange.willAccept(MatcherData("", 0, -2))).toBe(false)
    expect(RatingMatcher.IsInTheRange.willAccept(MatcherData("", 100, 10))).toBe(false)
  }

  @Test
  fun testFindById() {
    // IDs should never change
    listOf(
      1 to RatingMatcher.Is,
      2 to RatingMatcher.IsNot,
      3 to RatingMatcher.IsGreaterThan,
      4 to RatingMatcher.IsLessThan,
      5 to RatingMatcher.IsInTheRange,
    ).forEach { pair ->
      expect(RatingMatcher.fromId(pair.first)).toBe(pair.second)
    }
  }

  @Test
  fun testAllValues() {
    listOf(
      RatingMatcher.Is,
      RatingMatcher.IsNot,
      RatingMatcher.IsGreaterThan,
      RatingMatcher.IsLessThan,
      RatingMatcher.IsInTheRange,

      ).let { list ->
      expect(RatingMatcher.ALL_VALUES.size).toBe(list.size)
      list.forEach { matcher -> expect(RatingMatcher.ALL_VALUES).toContain(matcher) }
    }
  }
}
