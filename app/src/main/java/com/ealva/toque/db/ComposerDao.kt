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

import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.ComposerDaoEvent.ComposerCreatedOrUpdated
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.isValid
import com.ealva.toque.persist.asComposerId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
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

data class ComposerIdName(val composerId: ComposerId, val composerName: ComposerName)

data class ComposerDescription(
  val composerId: ComposerId,
  val composerName: ComposerName,
  val songCount: Long
)

sealed class ComposerDaoEvent {
  data class ComposerCreatedOrUpdated(val composerId: ComposerId) : ComposerDaoEvent()
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
   * Creates or gets the ID for for the [composer]. Throws IllegalStateException if [composer] is
   * not found and cannot be inserted.
   */
  fun getOrCreateComposerId(
    txn: TransactionInProgress,
    composer: String,
    composerSort: String,
    createTime: Millis,
    upsertResults: AudioUpsertResults
  ): ComposerId

  fun deleteAll(txn: TransactionInProgress)
  fun deleteComposersWithNoMedia(txn: TransactionInProgress): Long
  suspend fun getAllComposers(limit: Limit = NoLimit): Result<List<ComposerDescription>, DaoMessage>
  suspend fun getNextComposer(name: ComposerName): Result<ComposerIdName, DaoMessage>
  suspend fun getPreviousComposer(name: ComposerName): Result<ComposerIdName, DaoMessage>
  suspend fun getRandomComposer(): Result<ComposerIdName, DaoMessage>

  suspend fun getNext(composerId: ComposerId): Result<ComposerId, DaoMessage>
  suspend fun getPrevious(composerId: ComposerId): Result<ComposerId, DaoMessage>
  suspend fun getMin(): Result<ComposerId, DaoMessage>
  suspend fun getMax(): Result<ComposerId, DaoMessage>
  suspend fun getRandom(): Result<ComposerId, DaoMessage>

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): ComposerDao = ComposerDaoImpl(db, dispatcher)
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

private class ComposerDaoImpl(
  private val db: Database,
  dispatcher: CoroutineDispatcher
) : ComposerDao {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  override val composerDaoEvents = MutableSharedFlow<ComposerDaoEvent>()

  private fun emit(event: ComposerDaoEvent) {
    scope.launch { composerDaoEvents.emit(event) }
  }

  override fun getOrCreateComposerId(
    txn: TransactionInProgress,
    composer: String,
    composerSort: String,
    createTime: Millis,
    upsertResults: AudioUpsertResults
  ): ComposerId = txn
    .getOrCreateComposer(composer, composerSort, createTime)
    .also { composerId ->
      if (composerId.isValid) upsertResults.alwaysEmit { emitUpdateOrCreated(composerId) }
    }

  fun emitUpdateOrCreated(composerId: ComposerId) {
    scope.launch { emit(ComposerCreatedOrUpdated(composerId)) }
  }

  override fun deleteAll(txn: TransactionInProgress) = txn.run {
    val count = ComposerTable.deleteAll()
    LOG.i { it("Deleted %d composers", count) }
  }

  override fun deleteComposersWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    ComposerTable.deleteWhere {
      literal(0) eq (ComposerMediaTable.selectCount { composerId eq id }).asExpression()
    }.delete()
  }

  private val songCountColumn = ComposerMediaTable.mediaId.count()
  override suspend fun getAllComposers(
    limit: Limit
  ): Result<List<ComposerDescription>, DaoMessage> =
    runSuspendCatching {
      db.query {
        ComposerTable
          .join(ComposerMediaTable, JoinType.INNER, ComposerTable.id, ComposerMediaTable.composerId)
          .selects { listOf(ComposerTable.id, ComposerTable.composer, songCountColumn) }
          .all()
          .groupBy { ComposerTable.composer }
          .orderByAsc { ComposerTable.composerSort }
          .limit(limit.value)
          .sequence {
            ComposerDescription(
              composerId = ComposerId(it[ComposerTable.id]),
              composerName = ComposerName(it[ComposerTable.composer]),
              songCount = it[songCountColumn]
            )
          }
          .toList()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getNextComposer(name: ComposerName): Result<ComposerIdName, DaoMessage> =
    runSuspendCatching {
      db.query {
        ComposerTable
          .selects { listOf(id, composer) }
          .where { composer greater name.value }
          .orderByAsc { composer }
          .limit(1)
          .sequence { ComposerIdName(ComposerId(it[id]), ComposerName(it[composer])) }
          .single()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getNext(composerId: ComposerId) = runSuspendCatching {
    db.query {
      ComposerTable
        .select(ComposerTable.id)
        .where { composer greater SELECT_COMPOSER_FROM_BIND_ID }
        .orderByAsc { composer }
        .limit(1)
        .longForQuery { it[BIND_COMPOSER_ID] = composerId.value }
        .asComposerId
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getPrevious(composerId: ComposerId) = runSuspendCatching {
    db.query {
      ComposerTable
        .select(ComposerTable.id)
        .where { composer less SELECT_COMPOSER_FROM_BIND_ID }
        .orderBy { composer by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_COMPOSER_ID] = composerId.value }
        .asComposerId
    }
  }.mapError { DaoExceptionMessage(it) }

  private val composerMin by lazy { ComposerTable.composer.min().alias("composer_min_alias") }
  override suspend fun getMin() = runSuspendCatching {
    db.query {
      ComposerTable
        .selects { listOf(id, composerMin) }
        .all()
        .limit(1)
        .sequence { ComposerId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  private val composerMax by lazy { ComposerTable.composer.max().alias("composer_max_alias") }
  override suspend fun getMax() = runSuspendCatching {
    db.query {
      ComposerTable
        .selects { listOf(id, composerMax) }
        .all()
        .limit(1)
        .sequence { ComposerId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getRandom(): Result<ComposerId, DaoMessage> = runSuspendCatching {
    db.query {
      ComposerTable
        .select(ComposerTable.id)
        .where { id inSubQuery ComposerTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asComposerId
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getPreviousComposer(
    name: ComposerName
  ): Result<ComposerIdName, DaoMessage> = runSuspendCatching {
    db.query { if (name.isEmpty()) doGetMaxComposer() else doGetPreviousComposer(name) }
  }.mapError { DaoExceptionMessage(it) }

  /**
   * Throws NoSuchElementException if there is no genre name < greater than [name]
   */
  private fun Queryable.doGetPreviousComposer(name: ComposerName): ComposerIdName = ComposerTable
    .selects { listOf(id, composer) }
    .where { composer less name.value }
    .orderBy { composer by Order.DESC }
    .limit(1)
    .sequence { ComposerIdName(ComposerId(it[id]), ComposerName(it[composer])) }
    .single()

  private fun Queryable.doGetMaxComposer(): ComposerIdName = ComposerTable
    .selects { listOf(id, composerMax) }
    .all()
    .limit(1)
    .sequence { ComposerIdName(ComposerId(it[id]), ComposerName(it[composerMax])) }
    .single()

  override suspend fun getRandomComposer(): Result<ComposerIdName, DaoMessage> =
    runSuspendCatching { db.query { doGetRandomComposer() } }.mapError { DaoExceptionMessage(it) }

  private fun Queryable.doGetRandomComposer(): ComposerIdName = ComposerTable
    .selects { listOf(id, composer) }
    .where { id inSubQuery ComposerTable.select(id).all().orderByRandom().limit(1) }
    .sequence { ComposerIdName(ComposerId(it[id]), ComposerName(it[composer])) }
    .single()

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
    ?.asComposerId

  private fun TransactionInProgress.getOrInsertComposer(
    newComposer: String,
    newComposerSort: String,
    createTime: Millis
  ): ComposerId = getOrInsertLock.withLock {
    getComposer(newComposer) ?: INSERT_COMPOSER.insert {
      it[composer] = newComposer
      it[composerSort] = newComposerSort
      it[createdTime] = createTime()
    }.asComposerId
  }
}

fun ComposerName.isEmpty(): Boolean = value.isEmpty()

private val BIND_COMPOSER_ID: BindExpression<Long> = bindLong()
private val SELECT_COMPOSER_FROM_BIND_ID: Expression<String> = ComposerTable
  .select(ComposerTable.composer)
  .where { id eq BIND_COMPOSER_ID }
  .limit(1)
  .asExpression()
