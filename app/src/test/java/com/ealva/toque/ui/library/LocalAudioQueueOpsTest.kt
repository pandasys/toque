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

@file:Suppress("PropertyName")

package com.ealva.toque.ui.library

import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.DaoResult
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.library.LocalAudioQueueOps.Op
import com.ealva.toque.ui.library.LocalAudioQueueOps.OpMessage
import com.ealva.toque.ui.main.Notification
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAudioQueueOpsTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private val queueStub = LocalAudioQueueViewModelStub()
  private lateinit var queueOps: LocalAudioQueueOps

  @Before
  fun setup() {
    queueOps = LocalAudioQueueOps(queueStub)
  }

  @Test
  fun testGetMediaListReturnsError() = runTest {
    Op.values().forEach { op ->
      var clearCalled = false
      when (
        val result = queueOps.doOp(op, { Err(NotImplementedError()) }, { clearCalled = true })
      ) {
        is Ok -> fail("Expected Err")
        is Err -> expect(result).toBeInstanceOf<DaoResult<NotImplementedError>>()
      }
      expect(clearCalled).toBe(false)
    }
  }

  @Test
  fun testGetMediaListReturnsEmptyList() = runTest {
    Op.values().forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(EMPTY_MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> fail("Expected Err")
        is Err -> expect(result).toBeInstanceOf<Err<OpMessage.EmptyList>>()
      }
      expect(clearCalled).toBe(false)
    }
  }

  @Test
  fun testDismissedReturned() = runTest {
    val ops = listOf(Op.Play, Op.Shuffle, Op.AddToPlaylist)
    queueStub._playReturn = PromptResult.Dismissed
    queueStub._shuffleReturn = PromptResult.Dismissed
    queueStub._addToPlaylistReturn = PromptResult.Dismissed
    ops.forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(PromptResult.Dismissed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(false)
    }
  }

  @Test
  fun testAlwaysReturnExecuted() = runTest {
    val alwaysExecOps = listOf(Op.PlayNext, Op.AddToUpNext)
    queueStub._playReturn = PromptResult.Dismissed
    queueStub._shuffleReturn = PromptResult.Dismissed
    queueStub._addToPlaylistReturn = PromptResult.Dismissed
    alwaysExecOps.forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(PromptResult.Executed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(true)
    }
  }

  @Test
  fun testExecutedReturned() = runTest {
    queueStub._playReturn = PromptResult.Executed
    queueStub._shuffleReturn = PromptResult.Executed
    queueStub._addToPlaylistReturn = PromptResult.Executed
    Op.values().forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(PromptResult.Executed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(true)
    }
  }
}

private class LocalAudioQueueViewModelStub : LocalAudioQueueViewModel {
  override val localAudioQueue: StateFlow<LocalAudioQueue> = MutableStateFlow(NullLocalAudioQueue)
  override val playUpNextAction: StateFlow<PlayUpNextAction> =
    MutableStateFlow(PlayUpNextAction.Prompt)
  override val queueSize: Int = 0

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

private val EMPTY_MEDIA_LIST = CategoryMediaList.EMPTY_ALL_LIST
private val MEDIA_LIST = CategoryMediaList(MediaId(100), CategoryToken.All)
