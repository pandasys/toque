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

import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.main.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalAudioQueueViewModelSpy : LocalAudioQueueViewModel {
  override val localAudioQueue: StateFlow<LocalAudioQueue> = MutableStateFlow(NullLocalAudioQueue)
  override val playUpNextAction: StateFlow<PlayUpNextAction> =
    MutableStateFlow(PlayUpNextAction.Prompt)
  override val queueSize: Int = 0

  var _isPlaying: Boolean = false
  override val isPlaying: Boolean
    get() = _isPlaying

  override fun emitNotification(notification: Notification) {}

  var _playReturn: PromptResult = PromptResult.Executed
  override suspend fun play(mediaList: CategoryMediaList): PromptResult {
    return _playReturn
  }

  var _shuffleReturn: PromptResult = PromptResult.Executed
  override suspend fun shuffle(mediaList: CategoryMediaList): PromptResult {
    return _shuffleReturn
  }

  override fun playNext(mediaList: CategoryMediaList) {
  }

  override fun addToUpNext(categoryMediaList: CategoryMediaList) {
  }

  var _addToPlaylistReturn: PromptResult =
    PromptResult.Executed

  override suspend fun addToPlaylist(mediaIdList: MediaIdList): PromptResult {
    return _addToPlaylistReturn
  }

  override fun showPrompt(prompt: DialogPrompt) {
  }

  override fun clearPrompt() {
  }
}
