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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.SearchTerm
import com.ealva.toque.sharedtest.CoroutineRule
import com.ealva.toque.sharedtest.SearchDaoSpy
import com.github.michaelbull.result.Ok
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchHistoryTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Test
  fun testCreateSearchHistoryEmpty() = runTest {
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(emptyList())
    }
    val history = SearchHistory(searchDao, coroutineRule.testDispatcher)
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    expect(history.historyFlow.value).toBeEmpty()
  }

  @Test
  fun testCreateSearchHistoryNotEmpty() = runTest {
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(listOf(SearchTerm("black"), SearchTerm("white")))
    }
    val searchHistory = SearchHistory(searchDao, coroutineRule.testDispatcher)
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    val history = searchHistory.historyFlow.value
    expect(history).toHaveSize(2)
    expect(history[0]).toBe(SearchTerm("black"))
    expect(history[1]).toBe(SearchTerm("white"))
  }

  @Test
  fun testAddTerm() = runTest {
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(emptyList())
      _setSearchHistoryReturn = Ok(true)
    }
    val searchHistory = SearchHistory(searchDao, coroutineRule.testDispatcher)
    val searchTerm = SearchTerm("rock")
    searchHistory.add(searchTerm)
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    expect(searchDao._setSearchHistoryCalled).toBeGreaterThan(0)
    val history = searchHistory.historyFlow.value
    expect(history).toHaveSize(1)
    expect(history[0]).toBe(searchTerm)
  }

  @Test
  fun testDeleteTerm() = runTest {
    val blackTerm = SearchTerm("black")
    val whiteTerm = SearchTerm("white")
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(listOf(blackTerm, whiteTerm))
      _setSearchHistoryReturn = Ok(true)
    }
    val searchHistory = SearchHistory(searchDao, coroutineRule.testDispatcher)
    searchHistory.delete(blackTerm)
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    expect(searchDao._setSearchHistoryCalled).toBeGreaterThan(0)
    val history = searchHistory.historyFlow.value
    expect(history).toHaveSize(1)
    expect(history[0]).toBe(whiteTerm)
  }

  @Test
  fun testAddSimilarTerm() = runTest {
    val otherTerm = SearchTerm("blah")
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(listOf(otherTerm, SearchTerm("ro")))
      _setSearchHistoryReturn = Ok(true)
    }
    val searchHistory = SearchHistory(searchDao, coroutineRule.testDispatcher)
    val firstTerm = SearchTerm("roc")
    val secondTerm = SearchTerm("rock")
    searchHistory.add(firstTerm)
    searchHistory.add(secondTerm)
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    expect(searchDao._setSearchHistoryCalled).toBeGreaterThan(1)
    val history = searchHistory.historyFlow.value
    expect(history).toHaveSize(2)
    expect(history[0]).toBe(secondTerm)
    expect(history[1]).toBe(otherTerm)
  }

  @Test
  fun testClearHistory() = runTest {
    val blackTerm = SearchTerm("black")
    val whiteTerm = SearchTerm("white")
    val searchDao = SearchDaoSpy().apply {
      _getSearchHistoryResult = Ok(listOf(blackTerm, whiteTerm))
      _setSearchHistoryReturn = Ok(true)
      _clearSearchHistoryReturn = Ok(2)
    }
    val searchHistory = SearchHistory(searchDao, coroutineRule.testDispatcher)
    searchHistory.clear()
    advanceUntilIdle()
    expect(searchDao._getSearchHistoryCalled).toBeGreaterThan(0)
    expect(searchDao._clearSearchHistoryCalled).toBeGreaterThan(0)
    val history = searchHistory.historyFlow.value
    expect(history).toBeEmpty()
  }
}
