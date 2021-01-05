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

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.debugRequire
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.where
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

inline class ComposerId(override val id: Long) : PersistentId

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toComposerId(): ComposerId {
  debugRequire(PersistentId.isValidId(this)) { "All IDs must be greater than 0 to be valid" }
  return ComposerId(this)
}

@Suppress("NOTHING_TO_INLINE")
inline class ComposerIdList(val idList: LongList) : Iterable<ComposerId> {
  inline val size: Int
    get() = idList.size

  inline operator fun plusAssign(genreId: ComposerId) {
    idList.add(genreId.id)
  }

  inline operator fun get(index: Int): ComposerId = ComposerId(idList.getLong(index))

  companion object {
    operator fun invoke(capacity: Int): ComposerIdList = ComposerIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<ComposerId> = idIterator(idList, ::ComposerId)
}

private val LOG by lazyLogger(ComposerDao::class)
private val getOrInsertLock: Lock = ReentrantLock()

interface ComposerDao {
  /**
   * Creates or gets the Ids for the list of genres. Throws IllegalStateException if a genre is
   * not found and cannot be inserted.
   */
  fun getOrCreateComposerId(
    txn: TransactionInProgress,
    composer: String,
    composerSort: String,
    createTime: Millis
  ): ComposerId

  fun deleteAll(txn: TransactionInProgress)
  fun deleteComposersWithNoMedia(txn: TransactionInProgress): Long

  companion object {
    operator fun invoke(): ComposerDao = ComposerDaoImpl()
  }
}

private val INSERT_COMPOSER = ComposerTable.insertValues {
  it[composer].bindArg()
  it[composerSort].bindArg()
  it[createdTime].bindArg()
}

private val QUERY_COMPOSER_ID = Query(
  ComposerTable.select(ComposerTable.id)
    .where { composer eq bindString() }
)

private class ComposerDaoImpl : ComposerDao {

  override fun getOrCreateComposerId(
    txn: TransactionInProgress,
    composer: String,
    composerSort: String,
    createTime: Millis
  ): ComposerId = txn.getOrCreateComposer(composer, composerSort, createTime)

  override fun deleteAll(txn: TransactionInProgress) = txn.run {
    val count = ComposerTable.deleteAll()
    LOG.i { it("Deleted %d composers", count) }
  }

  override fun deleteComposersWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    ComposerTable.deleteWhere {
      literal(0) eq (ComposerMediaTable.selectCount { composerId eq id }).asExpression()
    }.delete()
  }

  /**
   * Could be a race condition if two threads are trying to insert the same genre at the same time,
   * so use a pattern similar to double check locking. Try the query, if result is null obtain a
   * lock and query again. If result is null again insert under the assumption this thread won
   * the race to insert. The great majority of the time the first query succeeds and the lock is
   * avoided.
   */
  private fun TransactionInProgress.getOrCreateComposer(
    composer: String,
    composerSort: String,
    createTime: Millis
  ): ComposerId = getComposer(composer)?.toComposerId() ?: getOrInsertComposer(
    composer,
    composerSort,
    createTime
  ).toComposerId()

  private fun Queryable.getComposer(genre: String): Long? = QUERY_COMPOSER_ID
    .sequence({ it[0] = genre }) { it[id] }
    .singleOrNull()

  /**
   * Get a lock and try to insert. If another thread won the race to insert, query on failure. If
   * query fails throw IllegalStateException.
   */
  private fun TransactionInProgress.getOrInsertComposer(
    newComposer: String,
    newComposerSort: String,
    createTime: Millis
  ): Long = getOrInsertLock.withLock {
    getComposer(newComposer) ?: INSERT_COMPOSER.insert {
      it[composer] = newComposer
      it[composerSort] = newComposerSort
      it[createdTime] = createTime.value
    }
  }
}
