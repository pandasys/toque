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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.SearchTerm
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.library.LibraryCategoryTitle
import com.ealva.toque.ui.library.LibraryItemsActions
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable

@Suppress("unused")
private val LOG by lazyLogger(SearchScreen::class)

@Immutable
@Parcelize
data class SearchScreen(private val noArg: String = "") : ComposeKey(), ScopeKey.Child,
  KoinComponent {
  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    val appPrefs: AppPrefsSingleton = get(AppPrefs.QUALIFIER)
    with(serviceBinder) {
      add(
        SearchModel(
          audioMediaDao = get(),
          searchDao = get(),
          localAudioQueue = lookup(),
          goToNowPlaying = { appPrefs.instance().goToNowPlaying() },
          backstack = backstack
        )
      )
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    fun requestFocusShowKeyboard() {
      focusRequester.requestFocus()
      keyboardController?.show()
    }

    val viewModel = rememberService<SearchModel>()
    val state = viewModel.stateFlow.collectAsState()
//    val scrollConnection = remember { HeightResizeScrollConnection() }

    val list = state.value.results
    val query = state.value.query
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false),
      topBar = {
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
          SearchTextField(
            query = query,
            focusRequester = focusRequester,
            onQueryTextChange = { viewModel.search(it) },
            onQueryTextSubmit = { viewModel.search(it) },
            onClearText = {
              viewModel.search(TextFieldValue(text = ""))
              requestFocusShowKeyboard()
            },
            onBackPressed = { viewModel.goBack() })
          Spacer(modifier = Modifier.height(4.dp))
          LibraryItemsActions(
            itemCount = state.value.totalItemCount,
            selectedCount = state.value.selectedCount,
            inSelectionMode = state.value.inSelectionMode,
            viewModel = viewModel,
          )
        }
      }
    ) {
      LaunchedEffect(key1 = Unit) { requestFocusShowKeyboard() }
      if (query.text.isNotBlank()) {
        SearchItemList(list)
      } else {
        HistoryList(
          previousSearches = state.value.previousSearches,
          historyItemSelected = { term ->
            viewModel.search(TextFieldValue(term.value, TextRange(0, term.value.length)))
          },
          deleteTerm = { term -> viewModel.deleteFromHistory(term) }
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchItemList(list: Collection<SearchModel.SearchResult<*, *>>) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(
      start = 8.dp,
      top = 8.dp,
      bottom = config.getListBottomContentPadding(isExpanded = true),
      end = 8.dp
    )
  ) {
    list.forEach { searchResult ->
      if (searchResult.isNotEmpty()) {
        stickyHeader(searchResult.category) {
          Box(
            modifier = Modifier
              .background(toqueColors.background)
              .fillMaxWidth()
          ) {
            LibraryCategoryTitle(
              info = searchResult.category.libraryCategory,
              boxSize = 34.dp,
              iconSize = 24.dp,
              padding = PaddingValues(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 8.dp)
            )
          }
        }
        searchResult.items(this)
      }
    }
  }
}

@Composable
private fun HistoryList(
  previousSearches: List<SearchTerm>,
  historyItemSelected: (SearchTerm) -> Unit,
  deleteTerm: (SearchTerm) -> Unit
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .padding(top = 12.dp)
      .scrollable(state = scrollState, orientation = Orientation.Vertical)
  ) {
    previousSearches.forEach { searchTerm ->
      HistorySearchTerm(
        searchTerm = searchTerm,
        onClick = historyItemSelected,
        deleteTerm = deleteTerm
      )
    }
  }
}

@Composable
private fun HistorySearchTerm(
  modifier: Modifier = Modifier,
  searchTerm: SearchTerm,
  onClick: (SearchTerm) -> Unit,
  deleteTerm: (SearchTerm) -> Unit
) {
  Row(
    modifier = modifier
      .padding(horizontal = 18.dp)
      .fillMaxWidth()
      .clickable { onClick(searchTerm) },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = searchTerm.value,
      style = toqueTypography.subtitle1
    )
    IconButton(
      onClick = { deleteTerm(searchTerm) },
      content = {
        Icon(
          modifier = Modifier.size(24.dp),
          imageVector = Icons.Default.Close,
          contentDescription = "Back"
        )
      },
    )
  }
}
