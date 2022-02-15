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
import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.TextMatcher
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class TextMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testContains() {
    val matcher = TextMatcher.Contains
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album LIKE '%ZZ Top%' ESCAPE '\'""")
  }


  @Test
  fun testDoesNotContain() {
    val matcher = TextMatcher.DoesNotContain
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album NOT LIKE '%ZZ Top%' ESCAPE '\'""")
  }

  @Test
  fun testIs() {
    val matcher = TextMatcher.Is
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album = 'ZZ Top'""")
  }

  @Test
  fun testIsNot() {
    val matcher = TextMatcher.IsNot
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album <> 'ZZ Top'""")
  }

  @Test
  fun testBeginsWith() {
    val matcher = TextMatcher.BeginsWith
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album LIKE 'ZZ Top%' ESCAPE '\'""")
  }

  @Test
  fun testEndsWith() {
    val matcher = TextMatcher.EndsWith
    val clause = matcher.makeWhereClause(AlbumTable.albumTitle, MatcherData("ZZ Top", 0, 0))
    expect(clause.toString()).toBe("""Album.Album LIKE '%ZZ Top' ESCAPE '\'""")
  }

  @Test
  fun testWillAccept() {
    val matcher = TextMatcher.EndsWith
    expect(matcher.willAccept(MatcherData("ZZ Top", 0, 0))).toBe(true)
    expect(matcher.willAccept(MatcherData.EMPTY)).toBe(false)
  }

  @Test
  fun testFindById() {
    // IDs should never change
    listOf(
      1 to TextMatcher.Contains,
      2 to TextMatcher.DoesNotContain,
      3 to TextMatcher.Is,
      4 to TextMatcher.IsNot,
      5 to TextMatcher.BeginsWith,
      6 to TextMatcher.EndsWith
    ).forEach { pair ->
      expect(TextMatcher.fromId(pair.first)).toBe(pair.second)
    }
  }

  @Test
  fun testAllValues() {
    listOf(
      TextMatcher.Contains,
      TextMatcher.DoesNotContain,
      TextMatcher.Is,
      TextMatcher.IsNot,
      TextMatcher.BeginsWith,
      TextMatcher.EndsWith
    ).let { list ->
      expect(TextMatcher.ALL_VALUES.size).toBe(list.size)
      list.forEach { matcher -> expect(TextMatcher.ALL_VALUES).toContain(matcher) }
    }
  }
}
