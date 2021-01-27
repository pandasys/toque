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
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict

private val LOG by lazyLogger(GenreMediaDao::class)

interface GenreMediaDao {
  /**
   * Insert or replace all genres for [newMediaId]
   */
  fun replaceMediaGenres(
    txn: TransactionInProgress,
    genreIdList: GenreIdList,
    newMediaId: MediaId,
    createTime: Millis
  )

  fun deleteAll(txn: TransactionInProgress)

  companion object {
    operator fun invoke(): GenreMediaDao = GenreMediaDaoImpl()
  }
}

private val INSERT_GENRE_MEDIA = GenreMediaTable.insertValues(OnConflict.Replace) {
  it[genreId].bindArg()
  it[mediaId].bindArg()
  it[createdTime].bindArg()
}

private val BIND_MEDIA_ID = bindLong()
private val DELETE_MEDIA = GenreMediaTable.deleteWhere { mediaId eq BIND_MEDIA_ID }

private class GenreMediaDaoImpl : GenreMediaDao {
  override fun replaceMediaGenres(
    txn: TransactionInProgress,
    genreIdList: GenreIdList,
    newMediaId: MediaId,
    createTime: Millis
  ) = txn.run {
    DELETE_MEDIA.delete { it[BIND_MEDIA_ID] = newMediaId.id }
    genreIdList.forEach { newGenreId ->
      INSERT_GENRE_MEDIA.insert {
        it[genreId] = newGenreId.id
        it[mediaId] = newMediaId.id
        it[createdTime] = createTime.value
      }
    }
  }

  override fun deleteAll(txn: TransactionInProgress) = txn.run {
    val count = GenreMediaTable.deleteAll()
    LOG.i { it("Deleted %d genre/media associations", count) }
  }
}