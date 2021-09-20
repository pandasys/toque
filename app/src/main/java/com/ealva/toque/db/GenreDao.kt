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

import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.toGenreId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
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

private val LOG by lazyLogger(GenreDao::class)

sealed class GenreDaoEvent {
  data class GenresCreatedOrUpdated(val genreIdList: GenreIdList) : GenreDaoEvent()
}

data class GenreIdName(val genreId: GenreId, val genreName: GenreName)

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface GenreDao {
  val genreDaoEvents: SharedFlow<GenreDaoEvent>

  /**
   * Gets or creates the Ids for the list of genres. Throws IllegalStateException if a genre is
   * not found and cannot be inserted.
   */
  fun getOrCreateGenreIds(
    txn: TransactionInProgress,
    genreList: List<String>,
    createTime: Millis
  ): GenreIdList

  fun deleteAll(txn: TransactionInProgress)
  fun deleteGenresNotAssociateWithMedia(txn: TransactionInProgress): Long

  suspend fun getAllGenreNames(limit: Long): Result<List<GenreIdName>, DaoMessage>
  suspend fun getNextGenre(genreName: GenreName): Result<GenreIdName, DaoMessage>

  companion object {
    operator fun invoke(db: Database, dispatcher: CoroutineDispatcher? = null): GenreDao =
      GenreDaoImpl(db, dispatcher ?: Dispatchers.Main)
  }
}

private class GenreDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : GenreDao {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val getOrInsertLock: Lock = ReentrantLock()
  override val genreDaoEvents = MutableSharedFlow<GenreDaoEvent>()

  private fun emit(event: GenreDaoEvent) {
    scope.launch { genreDaoEvents.emit(event) }
  }

  override fun getOrCreateGenreIds(
    txn: TransactionInProgress,
    genreList: List<String>,
    createTime: Millis
  ): GenreIdList {
    val genreIdList = GenreIdList()
    return GenreIdList(genreList.size).also { list ->
      genreList.forEach { genre -> list += txn.getOrCreateGenre(genre, createTime, genreIdList) }
    }.also { if (genreIdList.isNotEmpty) emit(GenreDaoEvent.GenresCreatedOrUpdated(genreIdList)) }
  }

  override fun deleteAll(txn: TransactionInProgress) = txn.run {
    val count = GenreTable.deleteAll()
    LOG.i { it("Deleted %d genres", count) }
  }

  override fun deleteGenresNotAssociateWithMedia(txn: TransactionInProgress): Long = txn.run {
    GenreTable.deleteWhere {
      literal(0) eq (GenreMediaTable.selectCount { genreId eq id }).asExpression()
    }.delete()
  }

  override suspend fun getAllGenreNames(
    limit: Long
  ): Result<List<GenreIdName>, DaoMessage> = db.query {
    runCatching { doGetGenreNames(limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetGenreNames(limit: Long): List<GenreIdName> = GenreTable
    .selects { listOf(id, genre) }
    .all()
    .orderByAsc { genre }
    .limit(limit)
    .sequence { GenreIdName(it[id].toGenreId(), GenreName(it[genre])) }
    .toList()

  override suspend fun getNextGenre(
    genreName: GenreName
  ): Result<GenreIdName, DaoMessage> = db.query {
    runCatching {
      LOG.i { it("getNextGenre after %s", genreName) }
      doGetNextGenre(genreName)
    }.mapError { DaoExceptionMessage(it) }
  }

  /**
   * Throws NoSuchElementException if there is no genre name > greater than [previousGenre]
   */
  private fun Queryable.doGetNextGenre(
    previousGenre: GenreName
  ): GenreIdName = GenreTable
    .selects { listOf(id, genre) }
    .where { genre greater previousGenre.value }
    .orderByAsc { genre }
    .limit(1)
    .sequence { GenreIdName(GenreId(it[id]), GenreName(it[genre])) }
    .single()
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
    genreIdList: GenreIdList
  ): GenreId =
    getGenreId(genre) ?: getOrInsert(genre, createTime, genreIdList)

  private fun Queryable.getGenreId(genre: String): GenreId? = QUERY_GENRE_ID
    .sequence({ it[queryGenreNameBind] = genre }) { it[id] }
    .singleOrNull()
    ?.toGenreId()

  private fun TransactionInProgress.getOrInsert(
    newGenre: String,
    createTime: Millis,
    genreIdList: GenreIdList
  ): GenreId = getOrInsertLock.withLock {
    getGenreId(newGenre) ?: INSERT_GENRE.insert {
      it[genre] = newGenre
      it[createdTime] = createTime()
    }.toGenreId().also { id -> genreIdList += id }
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
