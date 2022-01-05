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

import com.ealva.toque.common.Millis
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.MediaId
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict

interface GenreMediaDao {
  /**
   * Insert or replace all genres for [newMediaId]
   */
  fun TransactionInProgress.replaceMediaGenres(
    genreIdList: GenreIdList,
    newMediaId: MediaId,
    createTime: Millis
  )

  companion object {
    operator fun invoke(): GenreMediaDao = GenreMediaDaoImpl()
  }
}

private val INSERT_GENRE_MEDIA = GenreMediaTable.insertValues(OnConflict.Ignore) {
  it[genreId].bindArg()
  it[mediaId].bindArg()
  it[createdTime].bindArg()
}

private val BIND_MEDIA_ID = bindLong()
private val DELETE_MEDIA = GenreMediaTable.deleteWhere { mediaId eq BIND_MEDIA_ID }

private class GenreMediaDaoImpl : GenreMediaDao {
  override fun TransactionInProgress.replaceMediaGenres(
    genreIdList: GenreIdList,
    newMediaId: MediaId,
    createTime: Millis
  ) {
    DELETE_MEDIA.delete { it[BIND_MEDIA_ID] = newMediaId.value }
    genreIdList.forEach { newGenreId ->
      INSERT_GENRE_MEDIA.insert {
        it[genreId] = newGenreId.value
        it[mediaId] = newMediaId.value
        it[createdTime] = createTime()
      }
    }
  }
}
