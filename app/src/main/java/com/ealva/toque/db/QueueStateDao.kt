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
import com.ealva.toque.common.Millis
import com.ealva.toque.db.QueueStateDao.Companion.UNINITIALIZED_STATE
import com.ealva.welite.db.Database
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindInt
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val LOG by lazyLogger(QueueStateTable::class)

inline class QueueId(val id: Long)

data class QueueState(
  val mediaId: MediaId,
  val queueIndex: Int,
  val playbackPosition: Millis
)

interface QueueStateDaoFactory {
  fun makeQueueStateDao(queueId: QueueId): QueueStateDao

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): QueueStateDaoFactory = QueueStateDaoFactoryImpl(db, dispatcher)
  }
}

private class QueueStateDaoFactoryImpl(
  private val db: Database,
  val dispatcher: CoroutineDispatcher?
) : QueueStateDaoFactory {
  override fun makeQueueStateDao(queueId: QueueId): QueueStateDao {
    return QueueStateDao(queueId, db, dispatcher)
  }
}

interface QueueStateDao {

  /**
   * Get the persisted state. It's expected this will be called once at startup
   */
  suspend fun getState(): DaoResult<QueueState>

  /**
   * Persist the state. Hands off the state ASAP and if invoked quickly enough only the last value
   * is persisted (conflated)
   */
  fun persistState(state: QueueState)

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
    val UNINITIALIZED_STATE = QueueState(MediaId.INVALID, 0, Millis.ZERO)

    /**
     * Default dispatcher should only be changed for tests
     */
    operator fun invoke(
      queueId: QueueId,
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): QueueStateDao = QueueStateDaoImpl(queueId, db, dispatcher)
  }
}

private class QueueStateDaoImpl(
  private val queueId: QueueId,
  private val db: Database,
  dispatcher: CoroutineDispatcher?
) : QueueStateDao {
  private var closed = false
  private val scope =
    CoroutineScope(dispatcher ?: Executors.newSingleThreadExecutor().asCoroutineDispatcher())
  private val stateFlow = MutableStateFlow(UNINITIALIZED_STATE)
  private val collectJob = scope.launch {
    stateFlow
      .onEach { state ->
        if (state.mediaId.isValid) {
          db.transaction {
            val updateResult = UPDATE.update {
              it[BIND_QUEUE_ID] = queueId.id
              it[BIND_MEDIA_ID] = state.mediaId.id
              it[BIND_QUEUE_INDEX] = state.queueIndex
              it[BIND_POSITION] = state.playbackPosition.value
            }
            if (updateResult < 1) {
              LOG.i { it("MediaServiceStateTable queueId:$queueId not initialized, inserting row") }
              val insertResult = QueueStateTable.insert {
                it[id] = queueId.id
                it[mediaId] = state.mediaId.id
                it[queueIndex] = state.queueIndex
                it[playbackPosition] = state.playbackPosition.value
              }
              if (insertResult < 1) {
                LOG.e { it("Update and insert fail, service state not persisted") }
              }
            }
          }
        }
      }
      .onCompletion { LOG.i { it("QueueStateDao $queueId completed") } }
      .collect()
  }

  override suspend fun getState(): DaoResult<QueueState> = db.transaction {
    check(!closed)
    runCatching { doGetState() }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun TransactionInProgress.doGetState(): QueueState {
    return QueueStateTable
      .selects { listOf(mediaId, queueIndex, playbackPosition) }
      .where { id eq queueId.id }
      .sequence { QueueState(MediaId(it[mediaId]), it[queueIndex], Millis(it[playbackPosition])) }
      .singleOrNull()
      ?: UNINITIALIZED_STATE
  }

  override fun persistState(state: QueueState) {
    check(!closed) { "" }
    stateFlow.value = state
  }

  override fun close() {
    closed = true
    collectJob.cancel()
  }

  override val isClosed: Boolean
    get() = closed
}

private val BIND_QUEUE_ID = bindLong()
private val BIND_MEDIA_ID = bindLong()
private val BIND_QUEUE_INDEX = bindInt()
private val BIND_POSITION = bindLong()
private val UPDATE = QueueStateTable.updateColumns {
  it[mediaId] = BIND_MEDIA_ID
  it[queueIndex] = BIND_QUEUE_INDEX
  it[playbackPosition] = BIND_POSITION
}.where { id eq BIND_QUEUE_ID }
