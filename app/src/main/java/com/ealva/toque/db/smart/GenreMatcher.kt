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
import com.ealva.toque.db.GenreTable
import com.ealva.toque.db.HasTextSearchType
import com.ealva.toque.db.TextSearchType
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize

@Parcelize
enum class GenreMatcher(
  override val id: Int,
  private val stringRes: Int,
  override val searchType: TextSearchType
) : Matcher<String>, HasTextSearchType {
  Contains(1, R.string.contains, TextSearchType.Contains),
  DoesNotContain(2, R.string.does_not_contain, TextSearchType.DoesNotContain),
  Is(3, R.string.is_, TextSearchType.Is),
  IsNot(4, R.string.is_not, TextSearchType.IsNot),
  BeginsWith(5, R.string.begins_with, TextSearchType.BeginsWith),
  EndsWith(6, R.string.ends_with, TextSearchType.EndsWith);

  override fun willAccept(data: MatcherData): Boolean = data.text.isNotBlank()

  override fun makeWhereClause(column: Column<String>, data: MatcherData): Op<Boolean> =
    searchType.makeWhereOp(GenreTable.genre, data.text)

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<GenreMatcher> = values().toList()

    fun fromId(matcherId: Int): GenreMatcher = ALL_VALUES.find { it.id == matcherId }
      ?: throw IllegalArgumentException("No matcher with id=$matcherId")
  }
}
