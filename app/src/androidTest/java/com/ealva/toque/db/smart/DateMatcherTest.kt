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

package com.ealva.toque.db.smart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.smart.DateMatcher
import com.ealva.toque.db.smart.TheLast
import com.ealva.toque.db.smart.MatcherData
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DateMatcherTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testIs() {
    val matcher = DateMatcher.Is
    val clause = matcher.makeWhereClause(AlbumTable.updatedTime, MatcherData("", firstDate, 0L))
    expect(clause.toString())
      .toBe("Album.AlbumUpdated BETWEEN $firstDateMin AND $firstDateMax")
  }

  @Test
  fun testIsNot() {
    val matcher = DateMatcher.IsNot
    val clause = matcher.makeWhereClause(AlbumTable.updatedTime, MatcherData("", secondDate, 0L))
    expect(clause.toString())
      .toBe("Album.AlbumUpdated NOT BETWEEN $secondDateMin AND $secondDateMax")
  }

  @Test
  fun testIsAfter() {
    val matcher = DateMatcher.IsAfter
    val clause = matcher.makeWhereClause(AlbumTable.updatedTime, MatcherData("", firstDate, 0L))
    expect(clause.toString())
      .toBe("Album.AlbumUpdated > $firstDateMax")
  }

  @Test
  fun testIsBefore() {
    val matcher = DateMatcher.IsBefore
    val clause = matcher.makeWhereClause(AlbumTable.updatedTime, MatcherData("", firstDate, 0L))
    expect(clause.toString())
      .toBe("Album.AlbumUpdated < $firstDateMin")
  }

  @Test
  fun testInTheLast() {
    val matcher = DateMatcher.InTheLast
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(5), TheLast.Days)
    ).let { clause ->
      expect(clause.toString())
        .toBe("Album.AlbumUpdated >= (strftime('%s','now','-5 days') * 1000)")
    }
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(3), TheLast.Weeks)
    ).let { clause ->
      expect(clause.toString())
        .toBe("Album.AlbumUpdated >= (strftime('%s','now','-21 days') * 1000)")
    }
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(2), TheLast.Months)
    ).let { clause ->
      expect(clause.toString())
        .toBe("Album.AlbumUpdated >= (strftime('%s','now','-2 months') * 1000)")
    }
  }

  @Test
  fun testNotInTheLast() {
    val matcher = DateMatcher.NotInTheLast
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(5), TheLast.Days)
    ).let { clause ->
      expect(clause.toString())
        .toBe("""Album.AlbumUpdated < (strftime('%s','now','-5 days') * 1000)""")
    }
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(3), TheLast.Weeks)
    ).let { clause ->
      expect(clause.toString())
        .toBe("Album.AlbumUpdated < (strftime('%s','now','-21 days') * 1000)")
    }
    matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData(TheLast.Units(2), TheLast.Months)
    ).let { clause ->
      expect(clause.toString())
        .toBe("Album.AlbumUpdated < (strftime('%s','now','-2 months') * 1000)")
    }
  }

  @Test
  fun testIsInTheRange() {
    val matcher = DateMatcher.IsInTheRange
    val clause = matcher.makeWhereClause(
      AlbumTable.updatedTime,
      MatcherData("", firstDate, secondDate)
    )
    expect(clause.toString())
      .toBe("Album.AlbumUpdated BETWEEN $firstDateMin AND $secondDateMax")
  }

  @Test
  fun testWillAccept() {
    expect(DateMatcher.Is.willAccept(MatcherData("", -1L, 0L))).toBe(false)
    expect(DateMatcher.IsNot.willAccept(MatcherData("", -1L, 0L))).toBe(false)
    expect(DateMatcher.IsBefore.willAccept(MatcherData("", -1L, 0L))).toBe(false)
    expect(DateMatcher.IsAfter.willAccept(MatcherData("", -1L, 0L))).toBe(false)
    expect(DateMatcher.InTheLast.willAccept(MatcherData("", 100L, 5L))).toBe(false)
    expect(DateMatcher.NotInTheLast.willAccept(MatcherData("", 20, 10))).toBe(false)
    expect(DateMatcher.IsInTheRange.willAccept(MatcherData("", 10, 20))).toBe(true)
    expect(DateMatcher.IsInTheRange.willAccept(MatcherData("", 20, 10))).toBe(false)
  }

  @Test
  fun testFindById() {
    // IDs should never change
    listOf(
      1 to DateMatcher.Is,
      2 to DateMatcher.IsNot,
      3 to DateMatcher.IsAfter,
      4 to DateMatcher.IsBefore,
      5 to DateMatcher.InTheLast,
      6 to DateMatcher.NotInTheLast,
      7 to DateMatcher.IsInTheRange
    ).forEach { pair ->
      expect(DateMatcher.fromId(pair.first)).toBe(pair.second)
    }
  }

  @Test
  fun testAllValues() {
    listOf(
      DateMatcher.Is,
      DateMatcher.IsNot,
      DateMatcher.IsAfter,
      DateMatcher.IsBefore,
      DateMatcher.InTheLast,
      DateMatcher.NotInTheLast,
      DateMatcher.IsInTheRange
    ).let { list ->
      expect(DateMatcher.ALL_VALUES.size).toBe(list.size)
      list.forEach { matcher -> expect(DateMatcher.ALL_VALUES).toContain(matcher) }
    }
  }
}

private val zonedFirstDate = ZonedDateTime.of(1985, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)

// 487684800000 At noon on that day
private val firstDate: Long = zonedFirstDate
  .toInstant()
  .toEpochMilli()

private val firstDateMin = zonedFirstDate
  .with(LocalTime.MIN)
  .toInstant()
  .toEpochMilli()

private val firstDateMax = zonedFirstDate
  .with(LocalTime.MAX)
  .toInstant()
  .toEpochMilli()

private val zonedSecondDate = ZonedDateTime.of(1999, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)

// 916401600000 At noon on that day
private val secondDate: Long = zonedSecondDate
  .toInstant()
  .toEpochMilli()

private val secondDateMin = zonedSecondDate
  .with(LocalTime.MIN)
  .toInstant()
  .toEpochMilli()

private val secondDateMax = zonedSecondDate
  .with(LocalTime.MAX)
  .toInstant()
  .toEpochMilli()
