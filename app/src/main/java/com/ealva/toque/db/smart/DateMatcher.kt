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

package com.ealva.toque.db.smart

import androidx.annotation.StringRes
import com.ealva.toque.R
import com.ealva.toque.common.Millis
import com.ealva.toque.common.fetch
import com.ealva.welite.db.expr.ComparisonOp
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.between
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.notBetween
import com.ealva.welite.db.expr.wrapAsExpression
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Parcelize
enum class DateMatcher(
  override val id: Int,
  @StringRes private val stringRes: Int
) : Matcher<Long> {
  Is(1, R.string.is_) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      val date = Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC)
      return column.between(date.startOfDayMillis(), date.endOfDayMillis())
    }

    override fun sanitize(data: MatcherData): MatcherData =
      data.copy(first = Millis.currentUtcEpochMillis().value)
  },
  IsNot(2, R.string.is_not) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      val date = Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC)
      return column.notBetween(date.startOfDayMillis(), date.endOfDayMillis())
    }

    override fun sanitize(data: MatcherData): MatcherData =
      data.copy(first = Millis.currentUtcEpochMillis().value)
  },
  IsAfter(3, R.string.is_after) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column greater Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).endOfDayMillis()

    override fun sanitize(data: MatcherData): MatcherData =
      data.copy(first = Millis.currentUtcEpochMillis().value)
  },
  IsBefore(4, R.string.is_before) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column less Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).startOfDayMillis()

    override fun sanitize(data: MatcherData): MatcherData =
      data.copy(first = Millis.currentUtcEpochMillis().value)
  },
  InTheLast(5, R.string.in_the_last) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      return makeInTheLastClause(
        column = column,
        units = first,
        unitsType = second
      ) { t: Expression<Long> -> greaterEq(t) }
    }

    override fun willAccept(data: MatcherData): Boolean =
      data.first in 1..356 && TheLast.isValidId(data.second)

    override fun sanitize(data: MatcherData): MatcherData = data.copy(
      first = if (data.first in 1..356) data.first else 0,
      second = if (TheLast.isValidId(data.second)) data.second else
        TheLast.ALL_VALUES[0].id.toLong()
    )
  },
  NotInTheLast(6, R.string.not_in_the_last) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      return makeInTheLastClause(
        column = column,
        units = first,
        unitsType = second
      ) { t: Expression<Long> -> less(t) }
    }

    override fun willAccept(data: MatcherData): Boolean =
      data.first in 1..356 && TheLast.isValidId(data.second)

    override fun sanitize(data: MatcherData): MatcherData = data.copy(
      first = if (data.first in 1..356) data.first else 0,
      second = if (TheLast.isValidId(data.second)) data.second else
        TheLast.ALL_VALUES[0].id.toLong()
    )
  },
  IsInTheRange(7, R.string.is_in_the_range) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column.between(
        Instant.ofEpochMilli(first).atZone(ZoneOffset.UTC).startOfDayMillis(),
        Instant.ofEpochMilli(second).atZone(ZoneOffset.UTC).endOfDayMillis()
      )

    override fun willAccept(data: MatcherData): Boolean {
      return data.first >= 0 && data.second >= 0 && data.first <= data.second
    }

    override fun sanitize(data: MatcherData): MatcherData {
      return data.copy(
        first = if (data.first == 0L) Millis.currentUtcEpochMillis().value else data.first,
        second = if (data.second == 0L) Millis.currentUtcEpochMillis().value else data.second
      )
    }
  };

  override fun makeWhereClause(column: Column<Long>, data: MatcherData): Op<Boolean> =
    makeClause(column, data.first, data.second)

  protected abstract fun makeClause(
    column: Column<Long>,
    first: Long,
    second: Long
  ): Op<Boolean>

  override fun willAccept(data: MatcherData): Boolean {
    return data.first > 0 && data.second > 0
  }

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<DateMatcher> = values().toList()

    fun fromId(matcherId: Int): DateMatcher {
      return ALL_VALUES.find { it.id == matcherId }
        ?: throw IllegalArgumentException("No matcher with id=$matcherId")
    }

//    fun calendarToUtcStartOfDay(year: Int, month: Int, day: Int): Long = ZonedDateTime
//      .of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC)
//      .startOfDayMillis()
//
//    fun calendarToUtcEndOfDay(year: Int, month: Int, day: Int): Long = ZonedDateTime
//      .of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC)
//      .endOfDayMillis()

    /**
     * Build SQL to compare a date column to a value negatively offset from now, eg. 1 day ago or
     * 3 weeks ago, or 5 months ago, whatever the [units] and [unitsType] is. The [op]
     * operator would be '>=' if "in the last" or '<' if "not in the last". [units] must be a
     * positive number and [unitsType] must a valid [InTheLast.id] for the result to be
     * acceptable - [willAccept]
     */
    private fun makeInTheLastClause(
      column: Column<Long>,
      units: Long,
      unitsType: Long,
      op: SqlTypeExpression<Long>.(t: Expression<Long>) -> ComparisonOp
    ): Op<Boolean> {
      return with(TheLast.fromId(unitsType.toInt())) {
        // Tell Sqlite we want 'now' less a number of units of a particular type. strftime returns
        // seconds (%s seconds since 1970-01-01) so we multiply result by 1000 to get millis.
        column.op("(strftime('%s','now','${calc(units)} $unitsName') * 1000)".wrapAsExpression())
      }
    }
  }
}

private fun ZonedDateTime.startOfDayMillis(): Long = with(LocalTime.MIN)
  .toInstant()
  .toEpochMilli()

private fun ZonedDateTime.endOfDayMillis(): Long = with(LocalTime.MAX)
  .toInstant()
  .toEpochMilli()
