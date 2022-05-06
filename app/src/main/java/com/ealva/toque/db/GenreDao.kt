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

import com.ealva.ealvabrainz.common.GenreName
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.GenreDaoEvent.GenresCreatedOrUpdated
import com.ealva.toque.db.wildcard.SqliteLike.ESC_CHAR
import com.ealva.toque.db.wildcard.SqliteLike.likeEscaped
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asGenreId
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
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.countDistinct
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

sealed class GenreDaoEvent {
  data class GenresCreatedOrUpdated(val genreIdList: GenreIdList) : GenreDaoEvent()
}

data class GenreIdName(val genreId: GenreId, val genreName: GenreName)

data class GenreDescription(
  val genreId: GenreId,
  val genreName: GenreName,
  val songCount: Long,
  val duration: Duration
)

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface GenreDao {
  val genreDaoEvents: SharedFlow<GenreDaoEvent>

  fun TransactionInProgress.deleteAll(): Long
  fun TransactionInProgress.deleteGenresNotAssociateWithMedia(): Long

  suspend fun getAllGenres(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<GenreDescription>>

  suspend fun getAllGenreNames(limit: Limit = NoLimit): DaoResult<List<GenreIdName>>

  suspend fun getNext(genreId: GenreId): DaoResult<GenreId>
  suspend fun getPrevious(genreId: GenreId): DaoResult<GenreId>
  suspend fun getMin(): DaoResult<GenreId>
  suspend fun getMax(): DaoResult<GenreId>
  suspend fun getRandom(): DaoResult<GenreId>

  suspend fun getGenreSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  fun Transaction.replaceGenreMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  )

  companion object {
    operator fun invoke(db: Database, dispatcher: CoroutineDispatcher? = null): GenreDao =
      GenreDaoImpl(db, dispatcher ?: Dispatchers.Main)
  }
}

private class GenreDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : GenreDao {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val genreMediaDao = GenreMediaDao()
  private val getOrInsertLock: Lock = ReentrantLock()
  override val genreDaoEvents = MutableSharedFlow<GenreDaoEvent>()

  private fun emit(event: GenreDaoEvent) {
    scope.launch { genreDaoEvents.emit(event) }
  }

  override fun TransactionInProgress.deleteAll() = GenreTable.deleteAll()

  override fun TransactionInProgress.deleteGenresNotAssociateWithMedia(): Long = GenreTable
    .deleteWhere {
      literal(0) eq (GenreMediaTable.selectCount { genreId eq id }).asExpression()
    }.delete()

  private val songCountColumn = GenreMediaTable.mediaId.countDistinct()
  private val durationColumn = MediaTable.duration.sum()
  override suspend fun getAllGenres(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<GenreDescription>> = runSuspendCatching {
    db.query {
      GenreTable
        .join(GenreMediaTable, JoinType.INNER, GenreTable.id, GenreMediaTable.genreId)
        .join(MediaTable, JoinType.INNER, GenreMediaTable.mediaId, MediaTable.id)
        .selects { listOf(GenreTable.id, GenreTable.genre, songCountColumn, durationColumn) }
        .where { filter.whereCondition() }
        .groupBy { GenreTable.genre }
        .orderByAsc { GenreTable.genre }
        .limit(limit.value)
        .sequence { cursor ->
          GenreDescription(
            GenreId(cursor[GenreTable.id]),
            GenreName(cursor[GenreTable.genre]),
            cursor[songCountColumn],
            cursor[durationColumn]
          )
        }
        .toList()
    }
  }

  private fun Filter.whereCondition() = if (isBlank) null else GenreTable.genre.likeEscaped(value)

  override suspend fun getAllGenreNames(
    limit: Limit
  ): DaoResult<List<GenreIdName>> = runSuspendCatching {
    db.query {
      GenreTable
        .selects { listOf(id, genre) }
        .all()
        .orderByAsc { genre }
        .limit(limit.value)
        .sequence { cursor -> GenreIdName(cursor[id].asGenreId, GenreName(cursor[genre])) }
        .toList()
    }
  }

  override suspend fun getRandom(): DaoResult<GenreId> = runSuspendCatching {
    db.query {
      GenreTable
        .select(GenreTable.id)
        .where { id inSubQuery GenreTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asGenreId
    }
  }

  override suspend fun getGenreSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      GenreTable
        .select { genre }
        .where { textSearch.makeWhereOp(genre, partial) }
        .sequence { it[genre] }
        .toList()
    }
  }

  override suspend fun getNext(genreId: GenreId): DaoResult<GenreId> = runSuspendCatching {
    db.query {
      GenreTable
        .select(GenreTable.id)
        .where { genre greater SELECT_GENRE_FROM_BIND_ID }
        .orderByAsc { genre }
        .limit(1)
        .longForQuery { it[BIND_GENRE_ID] = genreId.value }
        .asGenreId
    }
  }

  override suspend fun getPrevious(genreId: GenreId): DaoResult<GenreId> = runSuspendCatching {
    db.query {
      GenreTable
        .select(GenreTable.id)
        .where { genre less SELECT_GENRE_FROM_BIND_ID }
        .orderBy { genre by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_GENRE_ID] = genreId.value }
        .asGenreId
    }
  }

  private val genreMin by lazy { GenreTable.genre.min().alias("genre_min_alias") }
  override suspend fun getMin(): DaoResult<GenreId> = runSuspendCatching {
    db.query {
      GenreTable
        .selects { listOf(id, genreMin) }
        .all()
        .limit(1)
        .sequence { cursor -> GenreId(cursor[id]) }
        .single()
    }
  }

  private val genreMax by lazy { GenreTable.genre.max().alias("genre_max_alias") }
  override suspend fun getMax(): DaoResult<GenreId> = runSuspendCatching {
    db.query {
      GenreTable
        .selects { listOf(id, genreMax) }
        .all()
        .limit(1)
        .sequence { cursor -> GenreId(cursor[id]) }
        .single()
    }
  }

  override fun Transaction.replaceGenreMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    with(genreMediaDao) {
      replaceMediaGenres(
        getOrCreateGenreIds(
          fileTagInfo.genres,
          createUpdateTime,
          upsertResults
        ),
        mediaId,
        createUpdateTime
      )
    }
  }

  private fun TransactionInProgress.getOrCreateGenreIds(
    genreList: List<String>,
    createTime: Millis,
    upsertResults: AudioUpsertResults
  ): GenreIdList {
    val genreIdList = GenreIdList()
    return GenreIdList(genreList.size).also { list ->
      genreList.forEach { genre ->
        list += getOrCreateGenre(
          genre,
          createTime,
          genreIdList
        )
      }
    }.also {
      if (genreIdList.isNotEmpty)
        upsertResults.alwaysEmit { emit(GenresCreatedOrUpdated(genreIdList)) }
    }
  }

  /**
   * Could be a race condition if two threads are trying to insert the same genre at the same time,
   * so use a pattern similar to double check locking. Try the query, if result is null obtain a
   * lock and query again. If result is null again, insert under the assumption this thread won
   * the race to insert. The great majority of the time the first query succeeds and the lock is
   * avoided.
   */
  private fun TransactionInProgress.getOrCreateGenre(
    genre: String,
    createTime: Millis,
    genreIdList: GenreIdList,
  ): GenreId =
    getGenreId(genre, genreIdList) ?: getOrInsert(genre, createTime, genreIdList)

  private fun Queryable.getGenreId(
    genre: String,
    genreIdList: GenreIdList
  ): GenreId? = QUERY_GENRE_ID
    .sequence({ bindings -> bindings[queryGenreNameBind] = genre }) { cursor -> cursor[id] }
    .singleOrNull()
    ?.asGenreId
    ?.also { genreId -> genreIdList += genreId }

  private fun TransactionInProgress.getOrInsert(
    newGenre: String,
    createTime: Millis,
    genreIdList: GenreIdList,
  ): GenreId = getOrInsertLock.withLock {
    getGenreId(newGenre, genreIdList) ?: INSERT_GENRE.insert {
      it[genre] = newGenre
      it[createdTime] = createTime()
    }.asGenreId.also { id -> genreIdList += id }
  }
}

private val INSERT_GENRE = GenreTable.insertValues {
  it[genre].bindArg()
  it[createdTime].bindArg()
}

private val queryGenreNameBind = bindString()

private val QUERY_GENRE_ID = Query(
  GenreTable.select(GenreTable.id)
    .where { genre eq queryGenreNameBind }
)

fun GenreName.isEmpty(): Boolean = value.isEmpty()

private val BIND_GENRE_ID: BindExpression<Long> = bindLong()
private val SELECT_GENRE_FROM_BIND_ID: Expression<String> = AlbumTable
  .select(GenreTable.genre)
  .where { id eq BIND_GENRE_ID }
  .limit(1)
  .asExpression()
