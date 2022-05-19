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

package com.ealva.toque.db

import android.os.Parcelable
import com.ealva.toque.db.SearchHistoryTable.id
import com.ealva.welite.db.Database
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.select
import com.github.michaelbull.result.coroutines.runSuspendCatching
import kotlinx.parcelize.Parcelize

@Parcelize
@JvmInline
value class SearchTerm(val value: String) : Parcelable

interface SearchDao {
  suspend fun getSearchHistory(): DaoResult<List<SearchTerm>>

  suspend fun setSearchHistory(terms: List<SearchTerm>): DaoResult<Boolean>

  suspend fun clearSearchHistory(): DaoResult<Long>

  companion object {
    operator fun invoke(db: Database): SearchDao = SearchDaoImpl(db)
  }
}


private class SearchDaoImpl(private val db: Database) : SearchDao {

  override suspend fun getSearchHistory(): DaoResult<List<SearchTerm>> = runSuspendCatching {
    db.query {
      SearchHistoryTable
        .select { searchTerm }
        .all()
        .orderByAsc { id }
        .sequence { cursor -> SearchTerm(cursor[searchTerm]) }
        .toList()
    }
  }

  override suspend fun setSearchHistory(
    terms: List<SearchTerm>
  ): DaoResult<Boolean> = runSuspendCatching {
    db.transaction {
      SearchHistoryTable.deleteAll()

      terms.forEachIndexed { index, term ->
        INSERT_STATEMENT.insert {
          it[id] = index
          it[searchTerm] = term.value
        }
      }
      true
    }
  }

  override suspend fun clearSearchHistory(): DaoResult<Long> = runSuspendCatching {
    db.transaction { SearchHistoryTable.deleteAll() }
  }
}

private val INSERT_STATEMENT = SearchHistoryTable.insertValues {
  it[id].bindArg()
  it[searchTerm].bindArg()
}

/**
 * SearchHistoryTable contains a simple ordered list of search strings. [id] will be used to sort
 * entries as changes to the table will be insert all and delete all, so we'll control the [id]
 */
object SearchHistoryTable : Table() {
  val id = integer("SearchHistory_id") { primaryKey() }
  val searchTerm = text("SearchTerm") { collateNoCase() }

  init {
    uniqueIndex(searchTerm)
  }
}
