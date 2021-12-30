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

import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.db.DaoCommon.ESC_CHAR
import com.ealva.toque.db.GenreTable
import com.ealva.toque.db.HasTextSearch
import com.ealva.toque.db.TextSearch
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.neq
import com.ealva.welite.db.expr.notLike
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize

@Parcelize
enum class GenreMatcher(
  override val id: Int,
  private val stringRes: Int
) : Matcher<String>, HasTextSearch {
  Contains(1, R.string.contains) {
    override val textSearch: TextSearch = TextSearch.Contains
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column like textSearch.applyWildcards(value) escape ESC_CHAR
  },
  DoesNotContain(2, R.string.does_not_contain) {
    override val textSearch: TextSearch = TextSearch.Contains
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column notLike textSearch.applyWildcards(value) escape ESC_CHAR
  },
  Is(3, R.string.is_) {
    override val textSearch: TextSearch = TextSearch.Contains
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column eq value
  },
  IsNot(4, R.string.is_not) {
    override val textSearch: TextSearch = TextSearch.Contains
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column neq value
  },
  BeginsWith(5, R.string.begins_with) {
    override val textSearch: TextSearch = TextSearch.BeginsWith
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column like textSearch.applyWildcards(value) escape ESC_CHAR
  },
  EndsWith(6, R.string.ends_with) {
    override val textSearch: TextSearch = TextSearch.EndsWith
    override fun makeClause(column: Column<String>, value: String): Op<Boolean> =
      column like textSearch.applyWildcards(value) escape ESC_CHAR
  };

  override fun willAccept(data: MatcherData): Boolean {
    return data.text.isNotBlank()
  }

  protected abstract fun makeClause(column: Column<String>, value: String): Op<Boolean>

  override fun makeWhereClause(column: Column<String>, data: MatcherData): Op<Boolean> =
    makeClause(GenreTable.genre, data.text.escapeWildcard())

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<GenreMatcher> = values().toList()

    fun fromId(matcherId: Int): GenreMatcher {
      return ALL_VALUES.find { it.id == matcherId }
        ?: throw IllegalArgumentException("No matcher with id=$matcherId")
    }
  }
}
