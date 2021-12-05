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
import com.ealva.toque.db.DaoNotImplemented
import com.ealva.toque.db.DaoResult
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.ui.audio.LocalAudioQueueModel
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAudioQueueOpsTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private val queueStub = LocalAudioQueueStub()
  private lateinit var queueOps: LocalAudioQueueOps

  @Before
  fun setup() {
    queueOps = LocalAudioQueueOps(queueStub)
  }

  @Test
  fun testGetMediaListReturnsError() = coroutineRule.runBlockingTest {
    Op.values().forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Err(DaoNotImplemented) }, { clearCalled = true })) {
        is Ok -> fail("Expected Err")
        is Err -> expect(result).toBeInstanceOf<DaoResult<DaoNotImplemented>>()
      }
      expect(clearCalled).toBe(false)
    }
  }

  @Test
  fun testGetMediaListReturnsEmptyList() = coroutineRule.runBlockingTest {
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
  fun testDismissedReturned() = coroutineRule.runBlockingTest {
    val ops = listOf(Op.Play, Op.Shuffle, Op.AddToPlaylist)
    queueStub._playReturn = LocalAudioQueueModel.PromptResult.Dismissed
    queueStub._shuffleReturn = LocalAudioQueueModel.PromptResult.Dismissed
    queueStub._addToPlaylistReturn = LocalAudioQueueModel.PromptResult.Dismissed
    ops.forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(LocalAudioQueueModel.PromptResult.Dismissed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(false)
    }
  }

  @Test
  fun testAlwaysReturnExecuted() = coroutineRule.runBlockingTest {
    val alwaysExecOps = listOf(Op.PlayNext, Op.AddToUpNext)
    queueStub._playReturn = LocalAudioQueueModel.PromptResult.Dismissed
    queueStub._shuffleReturn = LocalAudioQueueModel.PromptResult.Dismissed
    queueStub._addToPlaylistReturn = LocalAudioQueueModel.PromptResult.Dismissed
    alwaysExecOps.forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(LocalAudioQueueModel.PromptResult.Executed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(true)
    }
  }

  @Test
  fun testExecutedReturned() = coroutineRule.runBlockingTest {
    queueStub._playReturn = LocalAudioQueueModel.PromptResult.Executed
    queueStub._shuffleReturn = LocalAudioQueueModel.PromptResult.Executed
    queueStub._addToPlaylistReturn = LocalAudioQueueModel.PromptResult.Executed
    Op.values().forEach { op ->
      var clearCalled = false
      when (val result = queueOps.doOp(op, { Ok(MEDIA_LIST) }, { clearCalled = true })) {
        is Ok -> expect(result.value).toBe(LocalAudioQueueModel.PromptResult.Executed)
        is Err -> fail("Expected Ok")
      }
      expect(clearCalled).toBe(true)
    }
  }
}

private class LocalAudioQueueStub : LocalAudioQueueModel {
  override val localAudioQueue: StateFlow<LocalAudioQueue> = MutableStateFlow(NullLocalAudioQueue)
  override val playUpNextAction: StateFlow<PlayUpNextAction> =
    MutableStateFlow(PlayUpNextAction.Prompt)
  override val queueSize: Int = 0

  override fun emitNotification(notification: Notification) {}

  var _playReturn: LocalAudioQueueModel.PromptResult = LocalAudioQueueModel.PromptResult.Executed
  override suspend fun play(mediaList: CategoryMediaList): LocalAudioQueueModel.PromptResult {
    return _playReturn
  }

  var _shuffleReturn: LocalAudioQueueModel.PromptResult = LocalAudioQueueModel.PromptResult.Executed
  override suspend fun shuffle(mediaList: CategoryMediaList): LocalAudioQueueModel.PromptResult {
    return _shuffleReturn
  }

  override fun playNext(mediaList: CategoryMediaList) {
  }

  override fun addToUpNext(categoryMediaList: CategoryMediaList) {
  }

  var _addToPlaylistReturn: LocalAudioQueueModel.PromptResult =
    LocalAudioQueueModel.PromptResult.Executed
  override suspend fun addToPlaylist(mediaIdList: MediaIdList): LocalAudioQueueModel.PromptResult {
    return _addToPlaylistReturn
  }

  override fun showPrompt(prompt: DialogPrompt) {
  }

  override fun clearPrompt() {
  }
}

private val EMPTY_MEDIA_LIST = CategoryMediaList.EMPTY_ALL_LIST
private val MEDIA_LIST = CategoryMediaList(MediaId(100), CategoryToken.All)
