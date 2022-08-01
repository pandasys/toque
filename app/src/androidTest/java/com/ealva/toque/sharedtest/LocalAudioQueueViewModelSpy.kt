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

import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Millis
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.queue.QueueAudioItem
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
class LocalAudioQueueViewModelSpy : LocalAudioQueueViewModel {
  override val audioQueueState = MutableStateFlow(LocalAudioQueueState.NONE)
  override val playUpNextAction = MutableStateFlow(PlayUpNextAction.Prompt)
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

  override fun playNext(mediaList: CategoryMediaList) = Unit
  override fun addToUpNext(categoryMediaList: CategoryMediaList) = Unit

  var _addToPlaylistReturn: PromptResult =
    PromptResult.Executed

  override suspend fun addToPlaylist(mediaIdList: MediaIdList): PromptResult {
    return _addToPlaylistReturn
  }

  override fun showPrompt(prompt: DialogPrompt) = Unit
  override fun clearPrompt() = Unit
  override fun goToIndexMaybePlay(index: Int) = Unit
  override fun nextList() = Unit
  override fun next() = Unit
  override fun nextRepeatMode() = Unit
  override fun nextShuffleMode() = Unit
  override fun previousList() = Unit
  override fun previous() = Unit
  override fun seekTo(position: Millis) = Unit
  override fun toggleEqMode() = Unit
  override fun togglePlayPause() = Unit
  override fun moveQueueItem(from: Int, to: Int) = Unit
  override fun removeFromQueue(position: Int, item: QueueAudioItem) = Unit
  override fun setCurrentPreset(id: EqPresetId) = Unit
  override fun setPresetOverride(preset: EqPreset) = Unit
  override fun setPresetOverride(id: EqPresetId) = Unit
  override fun clearPresetOverride() = Unit
}
