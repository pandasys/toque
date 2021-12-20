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

package com.ealva.toque.ui.library

import androidx.compose.runtime.Composable

interface ActionsViewModel {
  fun selectAll()
  fun clearSelection()
  fun play()
  fun shuffle()
  fun playNext()
  fun addToUpNext()
  fun addToPlaylist()
}

@Composable
fun LibraryItemsActions(
  itemCount: Int,
  selectedItems: SelectedItems<*>,
  viewModel: ActionsViewModel
) {
  LibraryActionBar(
    itemCount = itemCount,
    inSelectionMode = selectedItems.inSelectionMode,
    selectedCount = selectedItems.selectedCount,
    play = { viewModel.play() },
    shuffle = { viewModel.shuffle() },
    playNext = { viewModel.playNext() },
    addToUpNext = { viewModel.addToUpNext() },
    addToPlaylist = { viewModel.addToPlaylist() },
    selectAllOrNone = { all -> if (all) viewModel.selectAll() else viewModel.clearSelection() },
    startSearch = {}
  )
}
