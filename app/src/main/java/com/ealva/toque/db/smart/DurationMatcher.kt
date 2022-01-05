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
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.between
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.notBetween
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS

@Suppress("unused")
@Parcelize
enum class DurationMatcher(
  override val id: Int,
  @StringRes private val stringRes: Int
) : Matcher<Long> {
  Is(1, R.string.is_) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      val seconds = TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(first))
      return column.between(seconds - 500, seconds + 499)
    }
  },
  IsNot(2, R.string.is_not) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> {
      val seconds = TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(first))
      return column.notBetween(seconds - 500, seconds + 499)
    }
  },
  IsGreaterThan(3, R.string.is_greater_than) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column greater TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(first)) + 499

  },
  IsLessThan(4, R.string.is_less_than) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column less TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(first)) - 500
  },
  IsInTheRange(5, R.string.is_in_the_range) {
    override fun makeClause(column: Column<Long>, first: Long, second: Long): Op<Boolean> =
      column.between(
        TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(first)) - 500,
        TimeUnit.SECONDS.toMillis(MILLISECONDS.toSeconds(second)) + 499
      )

    override fun willAccept(data: MatcherData): Boolean =
      data.first >= 0 && data.second > 0 && data.second > data.first

    override fun sanitize(data: MatcherData): MatcherData {
      val first = Millis(data.first).coerceAtLeast(Millis.ONE_SECOND)
      val second = Millis(data.second)
      return data.copy(
        first = first.value,
        second = second.coerceAtLeast(first + Millis.ONE_SECOND).value
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

  override fun willAccept(data: MatcherData): Boolean = data.first >= 0

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<DurationMatcher> = values().toList()

    fun fromId(matcherId: Int): DurationMatcher {
      return ALL_VALUES.find { it.id == matcherId }
        ?: throw IllegalArgumentException("No matcher with id=$matcherId")
    }
  }
}