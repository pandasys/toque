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
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.ComposerDaoEvent.ComposerCreatedOrUpdated
import com.ealva.toque.db.wildcard.SqliteLike.ESC_CHAR
import com.ealva.toque.db.wildcard.SqliteLike.likeEscaped
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asComposerId
import com.ealva.toque.persist.isValid
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.expr.sum
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Cursor
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
import kotlin.time.Duration

data class ComposerDescription(
  val composerId: ComposerId,
  val composerName: ComposerName,
  val songCount: Long,
  val duration: Duration
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

  fun TransactionInProgress.deleteAll(): Long
  fun TransactionInProgress.deleteComposersWithNoMedia(): Long

  fun TransactionInProgress.replaceMediaComposer(
    replaceComposerId: ComposerId,
    replaceMediaId: MediaId,
    createTime: Millis
  )

  suspend fun getAllComposers(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<ComposerDescription>>

  suspend fun getNext(composerId: ComposerId): DaoResult<ComposerId>
  suspend fun getPrevious(composerId: ComposerId): DaoResult<ComposerId>
  suspend fun getMin(): DaoResult<ComposerId>
  suspend fun getMax(): DaoResult<ComposerId>
  suspend fun getRandom(): DaoResult<ComposerId>

  suspend fun getComposerSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  fun Transaction.replaceComposerMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  )

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
  private val getOrInsertLock: Lock = ReentrantLock()
  private val composerMediaDao = ComposerMediaDao()
  override val composerDaoEvents = MutableSharedFlow<ComposerDaoEvent>()

  private fun emit(event: ComposerDaoEvent) {
    scope.launch { composerDaoEvents.emit(event) }
  }

  override fun Transaction.replaceComposerMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    replaceMediaComposer(
      getOrCreateComposerId(
        fileTagInfo.composer,
        fileTagInfo.composerSort,
        createUpdateTime,
        upsertResults
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun TransactionInProgress.getOrCreateComposerId(
    composer: String,
    composerSort: String,
    createTime: Millis,
    upsertResults: AudioUpsertResults
  ): ComposerId = getOrCreateComposer(composer, composerSort, createTime).also { composerId ->
    if (composerId.isValid) upsertResults.alwaysEmit { emitUpdateOrCreated(composerId) }
  }

  fun emitUpdateOrCreated(composerId: ComposerId) {
    scope.launch { emit(ComposerCreatedOrUpdated(composerId)) }
  }

  override fun TransactionInProgress.deleteAll() = ComposerTable.deleteAll()

  override fun TransactionInProgress.deleteComposersWithNoMedia(): Long = run {
    ComposerTable.deleteWhere {
      literal(0) eq (ComposerMediaTable.selectCount { composerId eq id }).asExpression()
    }.delete()
  }

  override fun TransactionInProgress.replaceMediaComposer(
    replaceComposerId: ComposerId,
    replaceMediaId: MediaId,
    createTime: Millis
  ) {
    with(composerMediaDao) { replaceMediaComposer(replaceComposerId, replaceMediaId, createTime) }
  }

  private val songCountColumn = ComposerMediaTable.mediaId.count()
  private val durationColumn = MediaTable.duration.sum()
  override suspend fun getAllComposers(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ComposerDescription>> = runSuspendCatching {
    db.query {
      ComposerTable
        .join(ComposerMediaTable, JoinType.INNER, ComposerTable.id, ComposerMediaTable.composerId)
        .join(MediaTable, JoinType.INNER, ComposerMediaTable.mediaId, MediaTable.id)
        .selects {
          listOf(ComposerTable.id, ComposerTable.composer, songCountColumn, durationColumn)
        }
        .where { filter.whereCondition() }
        .groupBy { ComposerTable.composer }
        .orderByAsc { ComposerTable.composerSort }
        .limit(limit.value)
        .sequence { cursor -> cursor.asComposerDescription }
        .toList()
    }
  }

  private fun Filter.whereCondition() = if (isBlank) null else
    ComposerTable.composer.likeEscaped(value)

  private val Cursor.asComposerDescription: ComposerDescription
    get() = ComposerDescription(
      composerId = ComposerId(this[ComposerTable.id]),
      composerName = ComposerName(this[ComposerTable.composer]),
      songCount = this[songCountColumn],
      duration = this[durationColumn]
    )

  override suspend fun getNext(composerId: ComposerId): DaoResult<ComposerId> = runSuspendCatching {
    db.query {
      ComposerTable
        .select { id }
        .where { composerSort greater SELECT_COMPOSER_SORT_FROM_BIND_ID }
        .orderByAsc { composerSort }
        .limit(1)
        .longForQuery { it[BIND_COMPOSER_ID] = composerId.value }
        .asComposerId
    }
  }

  override suspend fun getPrevious(
    composerId: ComposerId
  ): DaoResult<ComposerId> = runSuspendCatching {
    db.query {
      ComposerTable
        .select { id }
        .where { composerSort less SELECT_COMPOSER_SORT_FROM_BIND_ID }
        .orderBy { composerSort by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_COMPOSER_ID] = composerId.value }
        .asComposerId
    }
  }

  private val composerMin by lazy { ComposerTable.composerSort.min().alias("composer_min_alias") }

  override suspend fun getMin(): DaoResult<ComposerId> = runSuspendCatching {
    db.query {
      ComposerTable
        .selects { listOf(id, composerMin) }
        .all()
        .limit(1)
        .sequence { cursor -> ComposerId(cursor[id]) }
        .single()
    }
  }

  private val composerMax by lazy { ComposerTable.composerSort.max().alias("composer_max_alias") }
  override suspend fun getMax(): DaoResult<ComposerId> = runSuspendCatching {
    db.query {
      ComposerTable
        .selects { listOf(id, composerMax) }
        .all()
        .limit(1)
        .sequence { cursor -> ComposerId(cursor[id]) }
        .single()
    }
  }

  override suspend fun getRandom(): DaoResult<ComposerId> = runSuspendCatching {
    db.query {
      ComposerTable
        .select(ComposerTable.id)
        .where { id inSubQuery ComposerTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asComposerId
    }
  }

  override suspend fun getComposerSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      ComposerTable
        .select { composer }
        .where { textSearch.makeWhereOp(composer, partial) }
        .sequence { it[composer] }
        .toList()
    }
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
    .sequence({ bindings -> bindings[composerNameBind] = composer }) { cursor -> cursor[id] }
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
private val SELECT_COMPOSER_SORT_FROM_BIND_ID: Expression<String> = ComposerTable
  .select { composerSort }
  .where { id eq BIND_COMPOSER_ID }
  .limit(1)
  .asExpression()
