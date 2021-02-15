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
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.toGenreId
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
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(GenreDao::class)
private val getOrInsertLock: Lock = ReentrantLock()

private val INSERT_GENRE = GenreTable.insertValues {
  it[genre].bindArg()
  it[createdTime].bindArg()
}

private val queryGenreNameBind = bindString()

private val QUERY_GENRE_ID = Query(
  GenreTable.select(GenreTable.id)
    .where { genre eq queryGenreNameBind }
)

interface GenreDao {
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

  companion object {
    operator fun invoke(): GenreDao = GenreDaoImpl()
  }
}

private class GenreDaoImpl : GenreDao {

  override fun getOrCreateGenreIds(
    txn: TransactionInProgress,
    genreList: List<String>,
    createTime: Millis
  ): GenreIdList = GenreIdList(genreList.size).also { list ->
    genreList.forEach { genre -> list += txn.getOrCreateGenre(genre, createTime) }
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

  /**
   * Could be a race condition if two threads are trying to insert the same genre at the same time,
   * so use a pattern similar to double check locking. Try the query, if result is null obtain a
   * lock and query again. If result is null again, insert under the assumption this thread won
   * the race to insert. The great majority of the time the first query succeeds and the lock is
   * avoided.
   */
  private fun TransactionInProgress.getOrCreateGenre(genre: String, createTime: Millis): GenreId =
    getGenreId(genre)?.toGenreId() ?: getOrInsert(genre, createTime).toGenreId()

  private fun Queryable.getGenreId(genre: String): Long? = QUERY_GENRE_ID
    .sequence({ it[queryGenreNameBind] = genre }) { it[id] }
    .singleOrNull()

  /**
   * Get a lock and try to insert. If another thread won the race to insert, query on failure. If
   * query fails throw IllegalStateException.
   */
  private fun TransactionInProgress.getOrInsert(
    newGenre: String,
    createTime: Millis
  ): Long = getOrInsertLock.withLock {
    getGenreId(newGenre) ?: INSERT_GENRE.insert {
      it[genre] = newGenre
      it[createdTime] = createTime.value
    }
  }
}
