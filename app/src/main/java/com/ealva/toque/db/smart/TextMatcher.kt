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
import com.ealva.toque.common.fetch
import com.ealva.toque.db.HasTextSearch
import com.ealva.toque.db.TextSearch
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize

@Parcelize
enum class TextMatcher(
  override val id: Int,
  @StringRes private val stringRes: Int,
  override val textSearch: TextSearch
) : Matcher<String>, HasTextSearch {
  Contains(1, R.string.contains, TextSearch.Contains),
  DoesNotContain(2, R.string.does_not_contain, TextSearch.DoesNotContain),
  Is(3, R.string.is_, TextSearch.Is),
  IsNot(4, R.string.is_not, TextSearch.IsNot),
  BeginsWith(5, R.string.begins_with, TextSearch.BeginsWith),
  EndsWith(6, R.string.ends_with, TextSearch.EndsWith);

  override fun makeWhereClause(column: Column<String>, data: MatcherData): Op<Boolean> =
    textSearch.makeWhereOp(column, data.text)

  override fun willAccept(data: MatcherData): Boolean = data.text.isNotBlank()

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<TextMatcher> = values().toList()

    fun fromId(matcherId: Int): TextMatcher = ALL_VALUES.find { it.id == matcherId }
      ?: throw IllegalArgumentException("No matcher with id=$matcherId")
  }
}
