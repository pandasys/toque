/*
 * Copyright 2020 eAlva.com
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
import com.ealva.toque.common.toMillis
import com.ealva.toque.db.QueueId
import com.ealva.toque.db.QueueState
import com.ealva.toque.db.QueueStateDao
import com.ealva.toque.db.QueueStateDaoFactory
import com.ealva.toque.db.QueueStateTable
import com.ealva.toque.db.toMediaId
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
      val dao = QueueStateDao(FIRST_QUEUE, this, testDispatcher)
      val result = dao.getState()
      expect(result.get()).toBe(QueueStateDao.UNINITIALIZED_STATE)
    }
  }

  suspend fun testPersistState(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val dao = QueueStateDao(FIRST_QUEUE, this, testDispatcher)
      val mediaId = 1.toMediaId()
      val queueIndex = 5
      val playbackPosition = 5000.toMillis()
      val state = QueueState(mediaId, queueIndex, playbackPosition)
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state)
      }
      for (i in 1..10) {
        dao.persistState(QueueState(mediaId, queueIndex + i, playbackPosition))
      }
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(QueueState(mediaId, queueIndex + 10, playbackPosition))
      }
    }
  }

  suspend fun testFactory(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueueStateDaoFactory(this, testDispatcher)

      val daoOne = factory.makeQueueStateDao(FIRST_QUEUE)
      val state = QueueState(1.toMediaId(), 0, 1000.toMillis())
      daoOne.persistState(state)
      daoOne.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state)
      }

      val daoTwo = factory.makeQueueStateDao(SECOND_QUEUE)
      val state2 = QueueState(100.toMediaId(), 100, 5000.toMillis())
      daoTwo.persistState(state2)
      daoTwo.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state2)
      }
    }
  }

  suspend fun testCloseDaoAndRemake(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueueStateDaoFactory(this, testDispatcher)

      val dao = factory.makeQueueStateDao(FIRST_QUEUE)
      val state = QueueState(1.toMediaId(), 0, 1000.toMillis())
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state)
      }
      dao.close()

      val daoAgain = factory.makeQueueStateDao(FIRST_QUEUE)
      daoAgain.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state)
      }
    }
  }

  suspend fun testClosedDaoThrows(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, setOf(QueueStateTable), testDispatcher) {
      val factory = QueueStateDaoFactory(this, testDispatcher)

      val dao = factory.makeQueueStateDao(FIRST_QUEUE)
      val state = QueueState(1.toMediaId(), 0, 1000.toMillis())
      dao.persistState(state)
      dao.getState().let { result ->
        expect(result).toBeInstanceOf<Ok<QueueState>>()
        expect(result.get()).toBe(state)
      }

      dao.close()
      expect(dao.isClosed).toBe(true)
      dao.persistState(state)
    }
  }
}
