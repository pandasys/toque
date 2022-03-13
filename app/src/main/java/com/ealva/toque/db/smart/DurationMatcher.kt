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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * The user will specify duration in seconds, but will be stored in milliseconds. So,
 * [MatcherData.first] and [MatcherData.second] are [DurationUnit.MILLISECONDS] but effectively
 * rounded to seconds. Because the user deals with seconds but the media length is milliseconds, we
 * need to account for conversion of milliseconds to seconds. For example, [Is] (equals) must use
 * [between] because 500 milliseconds rounds up to a second boundary and 499 rounds down to a second
 * boundary.
 */
@Suppress("unused")
@Parcelize
enum class DurationMatcher(
  override val id: Int,
  @StringRes private val stringRes: Int
) : Matcher<Duration> {
  Is(1, R.string.is_) {
    override fun makeClause(column: Column<Duration>, first: Long, second: Long): Op<Boolean> {
      val millis = first.toDuration(DurationUnit.MILLISECONDS)
      return column.between(millis - 500.milliseconds, millis + 499.milliseconds)
    }
  },
  IsNot(2, R.string.is_not) {
    override fun makeClause(column: Column<Duration>, first: Long, second: Long): Op<Boolean> {
      val millis = first.toDuration(DurationUnit.MILLISECONDS)
      return column.notBetween(millis - 500.milliseconds, millis + 499.milliseconds)
    }
  },
  IsGreaterThan(3, R.string.is_greater_than) {
    override fun makeClause(column: Column<Duration>, first: Long, second: Long): Op<Boolean> {
      val millis = first.toDuration(DurationUnit.MILLISECONDS)
      return column greater (millis + 499.milliseconds)
    }
  },
  IsLessThan(4, R.string.is_less_than) {
    override fun makeClause(column: Column<Duration>, first: Long, second: Long): Op<Boolean> {
      val seconds = first.toDuration(DurationUnit.MILLISECONDS)
      return column less (seconds - 500.milliseconds)
    }
  },
  IsInTheRange(5, R.string.is_in_the_range) {
    override fun makeClause(column: Column<Duration>, first: Long, second: Long): Op<Boolean> {
      return column.between(
        first.toDuration(DurationUnit.MILLISECONDS) - 500.milliseconds,
        second.toDuration(DurationUnit.MILLISECONDS) + 499.milliseconds
      )
    }

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

  override fun makeWhereClause(column: Column<Duration>, data: MatcherData): Op<Boolean> =
    makeClause(column, data.first, data.second)

  protected abstract fun makeClause(
    column: Column<Duration>,
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
