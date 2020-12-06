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

import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.where
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

inline class GenreId(override val id: Long) : PersistentId

@Suppress("NOTHING_TO_INLINE")
inline class GenreIdList(val idList: LongList) : Iterable<GenreId> {
  inline val size: Int
    get() = idList.size

  inline operator fun plusAssign(genreId: GenreId) {
    idList.add(genreId.id)
  }

  inline operator fun get(index: Int): GenreId = GenreId(idList.getLong(index))

  companion object {
    operator fun invoke(capacity: Int): GenreIdList = GenreIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<GenreId> = idIterator(idList, ::GenreId)
}

private val LOG by lazyLogger(GenreDao::class)

interface GenreDao {
  /**
   * Creates or gets the Ids for the list of genres. Throws IllegalStateException if a genre is
   * not found and cannot be inserted.
   */
  suspend fun createOrGetGenreIds(
    txn: Transaction,
    genreList: List<String>,
    createTime: Long = System.currentTimeMillis()
  ): GenreIdList

  companion object {
    operator fun invoke(db: Database): GenreDao = GenreDaoImpl(db)
  }
}

private val INSERT_GENRE = GenreTable.insertValues {
  it[genre].bindArg()
  it[createdTime].bindArg()
}

private val QUERY_GENRE_ID: Query = Query(
  GenreTable.select(GenreTable.id)
    .where { GenreTable.genre eq bindString() }
)

private class GenreDaoImpl(private val db: Database) : GenreDao {
  private val insertOrGetLock: Mutex = Mutex(false)

  override suspend fun createOrGetGenreIds(
    txn: Transaction,
    genreList: List<String>,
    createTime: Long
  ): GenreIdList = GenreIdList(genreList.size).also { list ->
    genreList.forEach { genre -> list += txn.insertOrGetGenre(genre, createTime) }
  }

  /**
   * Could be a race condition if two threads are trying to insert the same genre at the same time,
   * so use a pattern similar to double check locking. Try the query, if result is null obtain a
   * lock and do the insert. If the insert fails query again under the assumption another thread won
   * the race to insert. The great majority of the time the first query succeeds and the lock is
   * avoided.
   */
  private suspend fun Transaction.insertOrGetGenre(genre: String, createTime: Long): GenreId =
    GenreId(getGenreId(genre) ?: getOrInsertGenreId(genre, createTime))

  private fun Queryable.getGenreId(genre: String): Long? = QUERY_GENRE_ID
    .sequence({ it[0] = genre }) { it[GenreTable.id] }
    .firstOrNull()

  /**
   * Get a lock and try to insert. If another thread won the race to insert, query on failure. If
   * query fails throw IllegalStateException.
   */
  private suspend fun Transaction.getOrInsertGenreId(
    genre: String,
    createTime: Long
  ): Long = insertOrGetLock.withLock {
    getGenreId(genre) ?: INSERT_GENRE.insert {
      LOG._e { it("inserting %s", genre) }
      it[0] = genre
      it[1] = createTime
    }
  }
}
