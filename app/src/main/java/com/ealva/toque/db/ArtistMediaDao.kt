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
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.MediaId
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict

interface ArtistMediaDao {
  /**
   * Insert or replace all artists for [replaceMediaId]
   */
  fun TransactionInProgress.replaceMediaArtists(
    artistIdList: ArtistIdList,
    replaceMediaId: MediaId,
    createTime: Millis
  )

  companion object {
    operator fun invoke(): ArtistMediaDao = ArtistMediaDaoImpl()
  }
}

private val INSERT_ARTIST_MEDIA = ArtistMediaTable.insertValues(OnConflict.Ignore) {
  it[artistId].bindArg()
  it[mediaId].bindArg()
  it[createdTime].bindArg()
}

private val BIND_MEDIA_ID = bindLong()
private val DELETE_MEDIA = ArtistMediaTable.deleteWhere { mediaId eq BIND_MEDIA_ID }

private class ArtistMediaDaoImpl : ArtistMediaDao {
  override fun TransactionInProgress.replaceMediaArtists(
    artistIdList: ArtistIdList,
    replaceMediaId: MediaId,
    createTime: Millis
  ) {
    DELETE_MEDIA.delete { it[BIND_MEDIA_ID] = replaceMediaId.value }
    artistIdList.forEach { newArtistId ->
      INSERT_ARTIST_MEDIA.insert {
        it[artistId] = newArtistId.value
        it[mediaId] = replaceMediaId.value
        it[createdTime] = createTime()
      }
    }
  }
}
