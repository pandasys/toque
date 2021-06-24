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
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.toComposerId
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
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(ComposerDao::class)
private val getOrInsertLock: Lock = ReentrantLock()

sealed class ComposerDaoEvent {
  data class ComposerCreated(val composerId: ComposerId) : ComposerDaoEvent()
}

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface ComposerDao {
  val composerDaoEvents: SharedFlow<ComposerDaoEvent>

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
    operator fun invoke(dispatcher: CoroutineDispatcher? = null): ComposerDao =
      ComposerDaoImpl(dispatcher ?: Dispatchers.Main)
  }
}

private val INSERT_COMPOSER = ComposerTable.insertValues {
  it[composer].bindArg()
  it[composerSort].bindArg()
  it[createdTime].bindArg()
}

private val composerNameBind = bindString()
private val QUERY_COMPOSER_ID = Query(
  ComposerTable.select(ComposerTable.id)
    .where { composer eq composerNameBind }
)

private class ComposerDaoImpl(dispatcher: CoroutineDispatcher) : ComposerDao {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  override val composerDaoEvents = MutableSharedFlow<ComposerDaoEvent>()

  private fun emit(event: ComposerDaoEvent) {
    scope.launch { composerDaoEvents.emit(event) }
  }

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
   * Could be a race condition if two threads are trying to insert the same composer at the same
   * time, so use a pattern similar to double check locking. Try the query, if result is null obtain
   * a lock and query again. If result is null again insert under the assumption this thread won the
   * race to insert. The great majority of the time the first query succeeds and the lock is
   * avoided.
   */
  private fun TransactionInProgress.getOrCreateComposer(
    composer: String,
    composerSort: String,
    createTime: Millis
  ): ComposerId = getComposer(composer) ?: getOrInsertComposer(
    composer,
    composerSort,
    createTime
  )

  private fun Queryable.getComposer(composer: String): ComposerId? = QUERY_COMPOSER_ID
    .sequence({ it[composerNameBind] = composer }) { it[id] }
    .singleOrNull()
    ?.toComposerId()

  private fun TransactionInProgress.getOrInsertComposer(
    newComposer: String,
    newComposerSort: String,
    createTime: Millis
  ): ComposerId = getOrInsertLock.withLock {
    getComposer(newComposer) ?: INSERT_COMPOSER.insert {
      it[composer] = newComposer
      it[composerSort] = newComposerSort
      it[createdTime] = createTime.value
    }.toComposerId().also { id -> emit(ComposerDaoEvent.ComposerCreated(id)) }
  }
}
