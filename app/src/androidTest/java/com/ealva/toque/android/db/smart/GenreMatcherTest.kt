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
import com.ealva.toque.db.smart.GenreMatcher
import com.ealva.toque.db.smart.MatcherData
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class GenreMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testContains() {
    val matcher = GenreMatcher.Contains
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString()).toBe(""""Genre"."Genre" LIKE '%Rock%' ESCAPE '\'""")
  }

  @Test
  fun testDoesNotContain() {
    val matcher = GenreMatcher.DoesNotContain
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString()).toBe(""""Genre"."Genre" NOT LIKE '%Rock%' ESCAPE '\'""")
  }

  @Test
  fun testIs() {
    val matcher = GenreMatcher.Is
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString())
      .toBe(""""Genre"."Genre" = 'Rock'""")
  }

  @Test
  fun testIsNot() {
    val matcher = GenreMatcher.IsNot
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString())
      .toBe(""""Genre"."Genre" <> 'Rock'""")
  }

  @Test
  fun testBeginsWith() {
    val matcher = GenreMatcher.BeginsWith
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString())
      .toBe(""""Genre"."Genre" LIKE 'Rock%' ESCAPE '\'""")
  }

  @Test
  fun testEndsWith() {
    val matcher = GenreMatcher.EndsWith
    val clause = matcher.makeWhereClause(MediaTable.genre, MatcherData("Rock", 0, 0))
    expect(clause.toString())
      .toBe(""""Genre"."Genre" LIKE '%Rock' ESCAPE '\'""")
  }

  @Test
  fun testWillAccept() {
    val matcher = GenreMatcher.EndsWith
    expect(matcher.willAccept(MatcherData.EMPTY)).toBe(false)
  }

  @Test
  fun testFindById() {
    // IDs should never change
    listOf(
      1 to GenreMatcher.Contains,
      2 to GenreMatcher.DoesNotContain,
      3 to GenreMatcher.Is,
      4 to GenreMatcher.IsNot,
      5 to GenreMatcher.BeginsWith,
      6 to GenreMatcher.EndsWith
    ).forEach { pair ->
      expect(GenreMatcher.fromId(pair.first)).toBe(pair.second)
    }
  }

  @Test
  fun testAllValues() {
    listOf(
      GenreMatcher.Contains,
      GenreMatcher.DoesNotContain,
      GenreMatcher.Is,
      GenreMatcher.IsNot,
      GenreMatcher.BeginsWith,
      GenreMatcher.EndsWith
    ).let { list ->
      expect(GenreMatcher.allValues.size).toBe(list.size)
      list.forEach { matcher -> expect(GenreMatcher.allValues).toContain(matcher) }
    }
  }
}
