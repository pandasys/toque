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

package com.ealva.toque.sharedtest

import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.SearchDao
import com.ealva.toque.db.SearchTerm
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok

@Suppress("PropertyName")
class SearchDaoSpy : SearchDao {
  var _getSearchHistoryCalled = 0
  var _getSearchHistoryResult: Result<List<SearchTerm>, Throwable> = Ok(emptyList())
  override suspend fun getSearchHistory(): DaoResult<List<SearchTerm>> {
    _getSearchHistoryCalled++
    return _getSearchHistoryResult
  }

  var _setSearchHistoryCalled = 0
  var _setSearchHistoryTerms: List<SearchTerm>? = null
  var _setSearchHistoryReturn: DaoResult<Boolean>? = null
  override suspend fun setSearchHistory(terms: List<SearchTerm>): DaoResult<Boolean> {
    _setSearchHistoryCalled++
    _setSearchHistoryTerms = terms
    return checkNotNull(_setSearchHistoryReturn)
  }

  var _clearSearchHistoryCalled = 0
  var _clearSearchHistoryReturn: DaoResult<Long>? = null
  override suspend fun clearSearchHistory(): DaoResult<Long> {
    _clearSearchHistoryCalled++
    return checkNotNull(_clearSearchHistoryReturn)
  }
}
