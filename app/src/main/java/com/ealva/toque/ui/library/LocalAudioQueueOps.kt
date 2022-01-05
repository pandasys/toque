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

import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIf

/**
 * This encapsulates the behavior of the various "play" or "add" functions as they are very similar
 * from the UI perspective. Gather the media to be operated on, return a value indicating if the
 * operation was executed, and if it was executed clear any user selection.
 */
class LocalAudioQueueOps(private val localAudioQueueModel: LocalAudioQueueViewModel) {
  enum class Op {
    Play {
      override suspend fun invoke(
        localAudioQueueModel: LocalAudioQueueViewModel,
        mediaList: CategoryMediaList
      ): PromptResult = localAudioQueueModel.play(mediaList)
    },
    Shuffle {
      override suspend fun invoke(
        localAudioQueueModel: LocalAudioQueueViewModel,
        mediaList: CategoryMediaList
      ): PromptResult = localAudioQueueModel.shuffle(mediaList)
    },
    PlayNext {
      override suspend fun invoke(
        localAudioQueueModel: LocalAudioQueueViewModel,
        mediaList: CategoryMediaList
      ): PromptResult {
        localAudioQueueModel.playNext(mediaList)
        return PromptResult.Executed
      }
    },
    AddToUpNext {
      override suspend fun invoke(
        localAudioQueueModel: LocalAudioQueueViewModel,
        mediaList: CategoryMediaList
      ): PromptResult {
        localAudioQueueModel.addToUpNext(mediaList)
        return PromptResult.Executed
      }
    },
    AddToPlaylist {
      override suspend fun invoke(
        localAudioQueueModel: LocalAudioQueueViewModel,
        mediaList: CategoryMediaList
      ): PromptResult = localAudioQueueModel.addToPlaylist(mediaList.idList)
    };

    abstract suspend operator fun invoke(
      localAudioQueueModel: LocalAudioQueueViewModel,
      mediaList: CategoryMediaList
    ): PromptResult
  }

  sealed interface OpMessage {
    data class DaoExceptionResult(val cause: Throwable) : OpMessage
    object EmptyList : OpMessage
  }

  suspend fun doOp(
    op: Op,
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    clearSelection: () -> Unit
  ): Result<PromptResult, OpMessage> = getMediaList()
    .mapError { daoMessage -> OpMessage.DaoExceptionResult(daoMessage) }
    .toErrorIf({ it.isEmpty }) { OpMessage.EmptyList }
    .map { list: CategoryMediaList -> op(localAudioQueueModel, list) }
    .onSuccess { if (it.wasExecuted) clearSelection() }
}