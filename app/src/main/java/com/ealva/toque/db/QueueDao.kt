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

package com.ealva.toque.db

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.asMillis
import com.ealva.toque.common.toDateTime
import com.ealva.toque.db.QueueItemsTable.itemId
import com.ealva.toque.db.QueueTable.queueId
import com.ealva.toque.db.QueueTable.updatedTime
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.MediaIdList
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError

private val LOG by lazyLogger(QueueDao::class)

interface QueueDao {
  /**
   * Delete all items for the queue and replace them [queueItems] and [shuffledItems]. The
   * [shuffledItems] list may be empty. Returns [Ok] if success
   */
  suspend fun replaceQueueItems(
    queue: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId> = emptyList(),
    updateTime: Millis = Millis.currentTime()
  ): BoolResult

  suspend fun replaceIfQueueUnchanged(
    queue: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId>,
    lastUpdated: Millis
  ): BoolResult

  suspend fun addQueueItems(
    queue: QueueId,
    mediaIds: MediaIdList,
    updateTime: Millis = Millis.currentTime()
  ): BoolResult

  suspend fun lastUpdatedTime(queue: QueueId): MillisResult

  companion object {
    operator fun invoke(db: Database): QueueDao =
      QueueDaoImpl(db)
  }
}

private val INSERT_QUEUE_ITEM = QueueItemsTable.insertValues {
  it[itemQueueId].bindArg()
  it[itemId].bindArg()
  it[shuffled].bindArg()
}

private class QueueDaoImpl(
  private val db: Database,
) : QueueDao {
  override suspend fun replaceQueueItems(
    queue: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId>,
    updateTime: Millis
  ): BoolResult = runSuspendCatching {
    db.transaction {
      setQueueLastUpdateTime(queue, updateTime)
      deleteAll(queue)
      insertList(queue, queueItems, isShuffled = false)
      insertList(queue, shuffledItems, isShuffled = true)
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun replaceIfQueueUnchanged(
    queue: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId>,
    lastUpdated: Millis
  ): BoolResult = runSuspendCatching {
    db.transaction {
      val queueUpdated = doGetLastUpdatedTime(queue)
      if (queueUpdated == lastUpdated) {
        setQueueLastUpdateTime(queue, Millis.currentTime())
        deleteAll(queue)
        insertList(queue, queueItems, isShuffled = false)
        insertList(queue, shuffledItems, isShuffled = true)
        true
      } else {
        LOG.e {
          it(
            "Queue changed since %s, last updated %s",
            lastUpdated.toDateTime(),
            queueUpdated.toDateTime()
          )
        }
        throw IllegalStateException("Queue has changed since ${lastUpdated.toDateTime()}")
      }
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun addQueueItems(
    queue: QueueId,
    mediaIds: MediaIdList,
    updateTime: Millis
  ): BoolResult = runSuspendCatching {
    db.transaction {
      setQueueLastUpdateTime(queue, updateTime)
      val iterator = mediaIds.value.iterator()
      while (iterator.hasNext()) {
        val mediaId = iterator.nextLong()
        if (
          QueueItemsTable.insert {
            it[itemQueueId] = queue.value
            it[itemId] = mediaId
            it[shuffled] = false
          } <= 0
        ) {
          rollback()
          throw DaoException("Failed to insert item:$itemId into queue:$queue")
        }
      }
      true
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun lastUpdatedTime(queue: QueueId): MillisResult = runSuspendCatching {
    db.query { doGetLastUpdatedTime(queue) }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  private fun Queryable.doGetLastUpdatedTime(queue: QueueId): Millis = QueueTable
    .select(updatedTime)
    .where { queueId eq queue.value }
    .sequence { cursor -> cursor[updatedTime] }
    .singleOrNull()?.asMillis() ?: throw IllegalStateException("No update time found for $queue")

  private fun Transaction.setQueueLastUpdateTime(queue: QueueId, updateTime: Millis) {
    QueueTable.updateColumns { it[updatedTime] = updateTime.value }
      .where { queueId eq queue.value }
      .update()
  }

  private fun TransactionInProgress.insertList(
    queue: QueueId,
    items: List<HasId>,
    isShuffled: Boolean
  ): Boolean {
    items.forEach { item ->
      INSERT_QUEUE_ITEM.insert {
        it[itemQueueId] = queue.value
        it[itemId] = item.id.value
        it[shuffled] = isShuffled
      }
    }
    return true
  }

  private fun TransactionInProgress.deleteAll(queueId: QueueId) =
    QueueItemsTable.deleteWhere { itemQueueId eq queueId.value }.delete()
}

/**
 * Contains a [queueId] primary key representing a particular queue and [updatedTime], the last time
 * the queue was updated. The items in a queue are in [QueueItemsTable]
 */
object QueueTable : Table() {
  /**
   * The ID of the queue, as there will be multiple queues - local audio, radio, video...
   */
  val queueId = integer("QueueId") { primaryKey() }

  /**
   * The last time [Millis.currentTime] the items of [queueId] in [QueueItemsTable] were
   * updated. This is initially used as a sanity check for "undo' functionality. If an undo request
   * is made, this time should be stored in the [Memento] so undo is aborted if a change has
   * occurred after this time.
   */
  val updatedTime = long("QueueUpdated") { default(0L) }
}

object QueueItemsTable : Table() {
  /**
   * Determines the order of items in the queue. Should only be used for ordering as it may start
   * at non-zero, have gaps, etc. Only purpose is to order items.
   */
  val queueOrder = long("QueueOrder") { primaryKey() }

  /**
   * The ID of the queue, as there will be multiple queues - local audio, radio, video...
   */
  val itemQueueId = reference(
    "QueueItems_QueueId",
    queueId,
    onDelete = ForeignKeyAction.CASCADE
  )

  /**
   * ID of the item in the queue. For media, would be MediaId.
   */
  val itemId = long("QueueItemId")

  /**
   * If true, the item is in the "shuffled" list.
   */
  val shuffled = bool("QueueIsShuffled")

  init {
    index(itemQueueId)
    index(shuffled)
  }
}
