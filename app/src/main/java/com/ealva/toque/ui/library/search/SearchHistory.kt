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

package com.ealva.toque.ui.library.search

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.SearchDao
import com.ealva.toque.db.SearchTerm
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("unused")
private val LOG by lazyLogger(SearchHistory::class)

private const val MAX_HISTORY = 10

interface SearchHistory {
  val historyFlow: StateFlow<List<SearchTerm>>

  /**
   * Adds the [searchTerm] to the history list. If [searchTerm] has a prefix in the list, the
   * prefix is removed, replaced with [searchTerm], and moved to the front of the list
   */
  suspend fun add(searchTerm: SearchTerm)
  suspend fun delete(searchTerm: SearchTerm)
  suspend fun clear(): Boolean

  companion object {
    operator fun invoke(
      searchDao: SearchDao,
      dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): SearchHistory = SearchHistoryImpl(searchDao, dispatcher)
  }
}

private class SearchHistoryImpl(
  private val searchDao: SearchDao,
  private val dispatcher: CoroutineDispatcher
) : SearchHistory {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  override val historyFlow = MutableStateFlow<List<SearchTerm>>(emptyList())

  init {
    scope.launch {
      historyFlow.value = searchDao.getSearchHistory()
        .onFailure { cause -> LOG.e(cause) { it("Error getting search history") } }
        .getOrElse { emptyList() }
    }
  }

  override suspend fun add(searchTerm: SearchTerm) = withContext(dispatcher) {
    val history = historyFlow.value.toMutableList()
    history.removeIf { term -> searchTerm.value.startsWith(term.value, ignoreCase = true) }
    history.add(0, searchTerm)
    val newHistory = if (history.size > MAX_HISTORY) history.take(MAX_HISTORY) else history
    if (newHistory != historyFlow.value) {
      searchDao.setSearchHistory(newHistory)
        .onFailure { cause -> LOG.e(cause) { it("Cannot persist search history") } }
        .onSuccess { success -> if (success) historyFlow.value = newHistory }
    }
  }

  override suspend fun delete(searchTerm: SearchTerm) = withContext(dispatcher) {
    val history = historyFlow.value.toMutableList()
    if (history.remove(searchTerm)) {
      searchDao.setSearchHistory(history)
        .onFailure { cause -> LOG.e(cause) { it("Cannot persist search history") } }
        .onSuccess { success -> if (success) historyFlow.value = history }
    }
  }

  override suspend fun clear(): Boolean = withContext(dispatcher) {
    if (historyFlow.value.isNotEmpty()) {
    searchDao.clearSearchHistory()
      .toErrorIf({ count -> count == 0L }) { IllegalStateException("Could not clear history") }
      .map { it > 0 }
      .onFailure { cause -> LOG.e(cause) { it("Error clearing search history") } }
      .onSuccess { historyFlow.value = emptyList() }
      .getOrElse { false }
    } else false
  }
}
