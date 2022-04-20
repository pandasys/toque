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
import com.ealva.toque.ui.library.LocalAudioQueueOps.OpMessage
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIf

interface LocalAudioQueueOps {
  sealed interface OpMessage {
    data class DaoExceptionResult(val cause: Throwable) : OpMessage
    object EmptyList : OpMessage
  }

  suspend fun play(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ): Result<PromptResult, OpMessage>

  suspend fun shuffle(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ): Result<PromptResult, OpMessage>

  suspend fun playNext(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  )

  suspend fun addToUpNext(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  )

  suspend fun addToPlaylist(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ): Result<PromptResult, OpMessage>

  companion object {
    operator fun invoke(localAudioQueueModel: LocalAudioQueueViewModel): LocalAudioQueueOps =
      LocalAudioQueueOpsImpl(localAudioQueueModel)
  }
}

private class LocalAudioQueueOpsImpl(
  private val localAudioQueueModel: LocalAudioQueueViewModel
) : LocalAudioQueueOps {
  override suspend fun play(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ): Result<PromptResult, OpMessage> = getListDoOp(getMediaList, onSuccess) { list ->
    localAudioQueueModel.play(list)
  }

  override suspend fun shuffle(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ): Result<PromptResult, OpMessage> = getListDoOp(getMediaList, onSuccess) { list ->
    localAudioQueueModel.shuffle(list)
  }

  override suspend fun playNext(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ) {
    getListDoOp(getMediaList, onSuccess) { list ->
      localAudioQueueModel.playNext(list)
      PromptResult.Executed
    }
  }

  override suspend fun addToUpNext(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ) {
    getListDoOp(getMediaList, onSuccess) { list ->
      localAudioQueueModel.addToUpNext(list)
      PromptResult.Executed
    }
  }

  override suspend fun addToPlaylist(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit
  ) = getListDoOp(getMediaList, onSuccess) { list ->
    localAudioQueueModel.addToPlaylist(list.idList)
  }

  private suspend fun getListDoOp(
    getMediaList: suspend () -> Result<CategoryMediaList, Throwable>,
    onSuccess: suspend () -> Unit,
    operation: suspend (CategoryMediaList) -> PromptResult
  ): Result<PromptResult, OpMessage> = getMediaList()
    .mapError { daoMessage -> OpMessage.DaoExceptionResult(daoMessage) }
    .toErrorIf({ it.isEmpty }) { OpMessage.EmptyList }
    .map { list: CategoryMediaList -> operation(list) }
    .onSuccess { promptResult -> if (promptResult.wasExecuted) onSuccess() }
}
