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

package com.ealva.toque.db

import com.ealva.toque.db.QueueTable.itemId
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.MediaIdList
import com.ealva.welite.db.Database
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Table
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import it.unimi.dsi.fastutil.longs.LongListIterator

interface QueueDao {
  /**
   * Delete all items for the queue and replace them [queueItems] and [shuffledItems]. The
   * [shuffledItems] list may be empty. Returns [Ok] if success
   */
  suspend fun replaceQueueItems(
    queueId: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId> = emptyList()
  ): BoolResult

  suspend fun addQueueItems(
    queueId: QueueId,
    mediaIds: MediaIdList
  ): BoolResult

  companion object {
    operator fun invoke(db: Database): QueueDao =
      QueueDaoImpl(db)
  }
}

private val INSERT_QUEUE_ITEM = QueueTable.insertValues {
  it[queueId].bindArg()
  it[itemId].bindArg()
  it[shuffled].bindArg()
}

private class QueueDaoImpl(
  private val db: Database,
) : QueueDao {
  override suspend fun replaceQueueItems(
    queueId: QueueId,
    queueItems: List<HasId>,
    shuffledItems: List<HasId>
  ): BoolResult = db.transaction {
    runCatching {
      deleteAll(queueId)
      insertList(queueId, queueItems, isShuffled = false)
      insertList(queueId, shuffledItems, isShuffled = true)
    }.mapError {
      rollback()
      DaoExceptionMessage(it)
    }
  }

  override suspend fun addQueueItems(
    queueId: QueueId,
    mediaIds: MediaIdList
  ): BoolResult = db.transaction {
    val iterator = mediaIds.value.iterator()
    runCatching { insertIds(iterator, queueId) }
      .mapError {
        rollback()
        DaoExceptionMessage(it)
      }
  }

  private fun Transaction.insertIds(
    iterator: LongListIterator,
    queueId: QueueId
  ): Boolean {
    while (iterator.hasNext()) {
      val mediaId = iterator.nextLong()
      if (
        QueueTable.insert {
          it[QueueTable.queueId] = queueId.value
          it[itemId] = mediaId
          it[shuffled] = false
        } <= 0
      ) {
        rollback()
        throw DaoException("Failed to insert item:$itemId into queue:$queueId")
      }
    }
    return true
  }

  private fun TransactionInProgress.insertList(
    queue: QueueId,
    items: List<HasId>,
    isShuffled: Boolean
  ): Boolean {
    items.forEach { item ->
      INSERT_QUEUE_ITEM.insert {
        it[queueId] = queue.value
        it[itemId] = item.id.value
        it[shuffled] = isShuffled
      }
    }
    return true
  }

  private fun TransactionInProgress.deleteAll(queueId: QueueId) =
    QueueTable.deleteWhere { QueueTable.queueId eq queueId.value }.delete()
}

/**
 * Stores the Up Next Queue for queue items. The queue position is determined by insertion order
 * as the column is a rowId column and is assigned largest rowId + 1 during insert. This provides
 * the ordering for a query. Example, to build the up next queue, query all [itemId] with
 * shuffled = false and order by queuePosition. To get the shuffled queue, use the same query
 * except shuffled = true. The queuePosition will not be an index into the resulting list but
 * simply an ordering number. An itemId may repeat in a queue based on user settings (controlled
 * in another scope) - eg. the local audio queue may hold duplicates
 */
object QueueTable : Table() {
  /**
   * Determines the order of items in the queue. Should only be used for ordering as it may start
   * at non-zero, have gaps, etc. Only purpose is to order items.
   */
  val queueOrder = long("QueueOrder") { primaryKey() }

  /**
   * The ID of the queue, as there will be multiple queues - local audio, radio, video...
   */
  val queueId = integer("QueueId")

  /**
   * ID of the item in the queue. For media, would be MediaId.
   */
  val itemId = long("QueueItemId")

  /**
   * If true, the item is in the "shuffled" list.
   */
  val shuffled = bool("QueueIsShuffled")

  init {
    index(queueId)
    index(shuffled)
  }
}
