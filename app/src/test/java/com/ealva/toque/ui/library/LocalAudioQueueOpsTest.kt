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
import com.ealva.toque.sharedTest.LocalAudioQueueViewModelSpy
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel.PromptResult
import com.ealva.toque.ui.library.LocalAudioQueueOps.OpMessage
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAudioQueueOpsTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private val queueStub = LocalAudioQueueViewModelSpy()
  private lateinit var queueOps: LocalAudioQueueOps

  @Before
  fun setup() {
    queueOps = LocalAudioQueueOps(queueStub)
  }

  @Test
  fun testPlayGetMediaListReturnsError() = runTest {
    var clearCalled = false
    expect(queueOps.play({ Err(NotImplementedError()) }, { clearCalled = true }))
      .toBeInstanceOf<DaoResult<NotImplementedError>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testShuffleGetMediaListReturnsError() = runTest {
    var clearCalled = false
    expect(queueOps.shuffle({ Err(NotImplementedError()) }, { clearCalled = true }))
      .toBeInstanceOf<DaoResult<NotImplementedError>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testPlayNextGetMediaListReturnsError() = runTest {
    var clearCalled = false
    queueOps.playNext({ Err(NotImplementedError()) }, { clearCalled = true })
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testAddToUpNextGetMediaListReturnsError() = runTest {
    var clearCalled = false
    queueOps.addToUpNext({ Err(NotImplementedError()) }, { clearCalled = true })
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testAddToPlaylistGetMediaListReturnsError() = runTest {
    var clearCalled = false
    expect(queueOps.addToPlaylist({ Err(NotImplementedError()) }, { clearCalled = true }))
      .toBeInstanceOf<DaoResult<NotImplementedError>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testPlayGetMediaListReturnsEmptyList() = runTest {
    var clearCalled = false
    expect(queueOps.play({ Ok(EMPTY_MEDIA_LIST) }, { clearCalled = true }))
      .toBeInstanceOf<Err<OpMessage.EmptyList>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testShuffleGetMediaListReturnsEmptyList() = runTest {
    var clearCalled = false
    expect(queueOps.shuffle({ Ok(EMPTY_MEDIA_LIST) }, { clearCalled = true }))
      .toBeInstanceOf<Err<OpMessage.EmptyList>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testAddToPlaylistGetMediaListReturnsEmptyList() = runTest {
    var clearCalled = false
    expect(queueOps.addToPlaylist({ Ok(EMPTY_MEDIA_LIST) }, { clearCalled = true }))
      .toBeInstanceOf<Err<OpMessage.EmptyList>>()
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testPlayDismissedReturned() = runTest {
    queueStub._playReturn = PromptResult.Dismissed
    queueStub._shuffleReturn = PromptResult.Dismissed
    queueStub._addToPlaylistReturn = PromptResult.Dismissed
    var clearCalled = false
    expect(queueOps.play({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Dismissed)
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testShuffleDismissedReturned() = runTest {
    queueStub._playReturn = PromptResult.Dismissed
    queueStub._shuffleReturn = PromptResult.Dismissed
    queueStub._addToPlaylistReturn = PromptResult.Dismissed
    var clearCalled = false
    expect(queueOps.shuffle({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Dismissed)
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testAddToPlaylistDismissedReturned() = runTest {
    queueStub._playReturn = PromptResult.Dismissed
    queueStub._shuffleReturn = PromptResult.Dismissed
    queueStub._addToPlaylistReturn = PromptResult.Dismissed
    var clearCalled = false
    expect(queueOps.addToPlaylist({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Dismissed)
    expect(clearCalled).toBe(false)
  }

  @Test
  fun testPlayExecutedReturned() = runTest {
    queueStub._playReturn = PromptResult.Executed
    queueStub._shuffleReturn = PromptResult.Executed
    queueStub._addToPlaylistReturn = PromptResult.Executed
    var clearCalled = false
    expect(queueOps.play({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Executed)
    expect(clearCalled).toBe(true)
  }

  @Test
  fun testShuffleExecutedReturned() = runTest {
    queueStub._playReturn = PromptResult.Executed
    queueStub._shuffleReturn = PromptResult.Executed
    queueStub._addToPlaylistReturn = PromptResult.Executed
    var clearCalled = false
    expect(queueOps.shuffle({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Executed)
    expect(clearCalled).toBe(true)
  }

  @Test
  fun testAddToPlaylistExecutedReturned() = runTest {
    queueStub._playReturn = PromptResult.Executed
    queueStub._shuffleReturn = PromptResult.Executed
    queueStub._addToPlaylistReturn = PromptResult.Executed
    var clearCalled = false
    expect(queueOps.addToPlaylist({ Ok(MEDIA_LIST) }, { clearCalled = true }).get())
      .toBe(PromptResult.Executed)
    expect(clearCalled).toBe(true)
  }
}

private val EMPTY_MEDIA_LIST = CategoryMediaList.EMPTY_ALL_LIST
private val MEDIA_LIST = CategoryMediaList(MediaId(100), CategoryToken.All)
