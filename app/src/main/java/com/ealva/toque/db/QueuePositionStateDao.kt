/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.db

import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.common.Millis
import com.ealva.toque.common.runSuspendCatching
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.isValid
import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.bindInt
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.UpdateStatement
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.Executors

private val LOG by lazyLogger(QueueStateTable::class)

@JvmInline
value class QueueId(val value: Int)

data class QueuePositionState(
  val mediaId: MediaId,
  val queueIndex: Int,
  val playbackPosition: Millis,
  val timePlayed: Millis = Millis(0),
  val countingFrom: Millis = Millis(0)
) {
  companion object {
    val INACTIVE_QUEUE_STATE = QueuePositionState(MediaId.INVALID, -1, Millis(0))
  }
}

interface QueuePositionStateDaoFactory {
  fun makeStateDao(queueId: QueueId): QueuePositionStateDao

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    ): QueuePositionStateDaoFactory = QueuePositionStateDaoFactoryImpl(db, dispatcher)
  }
}

private class QueuePositionStateDaoFactoryImpl(
  private val db: Database,
  private val dispatcher: CoroutineDispatcher
) : QueuePositionStateDaoFactory {
  override fun makeStateDao(queueId: QueueId): QueuePositionStateDao {
    return QueuePositionStateDao(queueId, db, dispatcher)
  }
}

interface QueuePositionStateDao {

  /**
   * Get the persisted state. It's expected this will be called once at startup
   */
  suspend fun getState(): DaoResult<QueuePositionState>

  /**
   * Persist the state. Hands off the state ASAP and if invoked quickly enough only the last value
   * is persisted (conflated)
   */
  fun persistState(state: QueuePositionState): QueuePositionState

  /**
   * Cancels the underlying state flow collector job and no further values will be persisted.
   * Typically called when the media service switches queues
   */
  fun close()

  /**
   * Has the underlying persistence flow been cancelled
   */
  val isClosed: Boolean

  companion object {
    /**
     * If no state has been persisted this is the value returned [getState]
     */
    val UNINITIALIZED_STATE = QueuePositionState.INACTIVE_QUEUE_STATE

    /**
     * Default dispatcher should only be changed for tests
     */
    operator fun invoke(
      queueId: QueueId,
      db: Database,
      dispatcher: CoroutineDispatcher
    ): QueuePositionStateDao = QueuePositionStateDaoImpl(queueId, db, dispatcher)
  }
}

private class QueuePositionStateDaoImpl(
  private val queueId: QueueId,
  private val db: Database,
  dispatcher: CoroutineDispatcher
) : QueuePositionStateDao {
  private var closed = false
  // State is persisted at a usual rate of 3 times per second on average. A burst would be 4.
  // We create our own thread to reduce latency and get our flow, which persists, off of the UI
  // thread as quickly as possible. There should be only 1 queue (queueId) active at a time. The
  // Dao layer will do a withContext(IO). Using a state flow so we get conflation as default
  // behavior (though I bet we rarely, if ever, miss one). Missing one at the end of a piece of
  // media playing means restore will be off by milliseconds. Not all queues need to use
  // time played and count from as they may not restore the same way, eg. radio station queue won't
  // restore to a position, only a stream.
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val stateFlow = MutableStateFlow(QueuePositionState.INACTIVE_QUEUE_STATE)
  private val collectJob = stateFlow
    .onEach { state -> handleNewQueueState(state) }
    .catch { cause -> LOG.e(cause) { it("Error processing QueueDao state flow") } }
    .onCompletion { LOG.i { it("QueueStateDao $queueId completed") } }
    .launchIn(scope)

  private suspend fun handleNewQueueState(state: QueuePositionState) {
    if (state.mediaId.isValid) {
      db.transaction {
        val updateResult = UPDATE.update {
          it[BIND_QUEUE_ID] = queueId.value
          it[BIND_MEDIA_ID] = state.mediaId.value
          it[BIND_QUEUE_INDEX] = state.queueIndex
          it[BIND_POSITION] = state.playbackPosition()
          it[BIND_TIME_PLAYED] = state.timePlayed()
          it[BIND_COUNT_FROM] = state.countingFrom()
        }
        if (updateResult < 1) {
          LOG.w { it("MediaServiceStateTable queueId:$queueId not initialized, inserting row") }
          val insertResult = QueueStateTable.insert {
            it[id] = queueId.value
            it[mediaId] = state.mediaId.value
            it[queueIndex] = state.queueIndex
            it[playbackPosition] = state.playbackPosition()
            it[timePlayed] = state.timePlayed()
            it[countingFrom] = state.countingFrom()
          }
          if (insertResult < 1) {
            LOG.e { it("Update and insert fail, service state not persisted") }
          }
        }
      }
    }
  }

  override suspend fun getState(): DaoResult<QueuePositionState> {
    check(!closed)
    return runSuspendCatching {
      db.transaction {
        QueueStateTable
          .selects { listOf(mediaId, queueIndex, playbackPosition, timePlayed, countingFrom) }
          .where { id eq this@QueuePositionStateDaoImpl.queueId.value }
          .sequence {
            QueuePositionState(
              MediaId(it[mediaId]),
              it[queueIndex],
              Millis(it[playbackPosition]),
              Millis(it[timePlayed]),
              Millis(it[countingFrom])
            )
          }
          .singleOrNull()
          ?: QueuePositionState.INACTIVE_QUEUE_STATE
      }
    }.mapError { DaoExceptionMessage(it) }
  }

  override fun persistState(state: QueuePositionState): QueuePositionState {
    check(!closed) { "Attempt to persist QueuePositionState after Dao closed" }
    stateFlow.value = state
    return state
  }

  override fun close() {
    closed = true
    collectJob.cancel()
  }

  override val isClosed: Boolean
    get() = closed
}

private val BIND_QUEUE_ID = bindInt()
private val BIND_MEDIA_ID = bindLong()
private val BIND_QUEUE_INDEX = bindInt()
private val BIND_POSITION = bindLong()
private val BIND_TIME_PLAYED = bindLong()
private val BIND_COUNT_FROM = bindLong()
private val UPDATE: UpdateStatement<QueueStateTable> = QueueStateTable.updateColumns {
  it[mediaId] = BIND_MEDIA_ID
  it[queueIndex] = BIND_QUEUE_INDEX
  it[playbackPosition] = BIND_POSITION
  it[timePlayed] = BIND_TIME_PLAYED
  it[countingFrom] = BIND_COUNT_FROM
}.where { id eq BIND_QUEUE_ID }
