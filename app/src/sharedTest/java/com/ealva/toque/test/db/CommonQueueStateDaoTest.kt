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

package com.ealva.toque.test.db

import android.content.Context
import com.ealva.toque.common.Millis
import com.ealva.toque.db.QueueId
import com.ealva.toque.db.QueuePositionState
import com.ealva.toque.db.QueuePositionStateDao
import com.ealva.toque.db.QueuePositionStateDaoFactory
import com.ealva.toque.db.QueueStateTable
import com.ealva.toque.persist.asMediaId
import com.ealva.toque.test.shared.withTestDatabase
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

private val FIRST_QUEUE = QueueId(1)
private val SECOND_QUEUE = QueueId(2)

object CommonQueueStateDaoTest {
  suspend fun testGetBeforePersist(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val dao = QueuePositionStateDao(FIRST_QUEUE, this, testDispatcher)
      val result = dao.getState()
      expect(result.get()).toBe(QueuePositionStateDao.UNINITIALIZED_STATE)
    }
  }

  suspend fun testPersistState(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val dao = QueuePositionStateDao(FIRST_QUEUE, this, testDispatcher)
      val mediaId = 1.asMediaId
      val queueIndex = 5
      val playbackPosition = Millis(5000)
      val state = QueuePositionState(mediaId, queueIndex, playbackPosition)
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state)
      }
      for (i in 1..10) {
        dao.persistState(QueuePositionState(mediaId, queueIndex + i, playbackPosition))
      }
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(QueuePositionState(mediaId, queueIndex + 10, playbackPosition))
      }
    }
  }

  suspend fun testFactory(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueuePositionStateDaoFactory(this, testDispatcher)

      val daoOne = factory.makeStateDao(FIRST_QUEUE)
      val state = QueuePositionState(1.asMediaId, 0, Millis(1000))
      daoOne.persistState(state)
      daoOne.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state)
      }

      val daoTwo = factory.makeStateDao(SECOND_QUEUE)
      val state2 = QueuePositionState(100.asMediaId, 100, Millis(5000))
      daoTwo.persistState(state2)
      daoTwo.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state2)
      }
    }
  }

  suspend fun testCloseDaoAndRemake(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueuePositionStateDaoFactory(this, testDispatcher)

      val dao = factory.makeStateDao(FIRST_QUEUE)
      val state = QueuePositionState(1.asMediaId, 0, Millis(1000))
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state)
      }
      dao.close()

      val daoAgain = factory.makeStateDao(FIRST_QUEUE)
      daoAgain.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state)
      }
    }
  }

  suspend fun testClosedDaoThrows(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueuePositionStateDaoFactory(this, testDispatcher)

      val dao = factory.makeStateDao(FIRST_QUEUE)
      val state = QueuePositionState(1.asMediaId, 0, Millis(1000))
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueuePositionState>>()
        expect(result.get()).toBe(state)
      }

      dao.close()
      expect(dao.isClosed).toBe(true)
      dao.persistState(state)
    }
  }
}
