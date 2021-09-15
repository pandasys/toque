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
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict

private val LOG by lazyLogger(ArtistAlbumDao::class)

interface ArtistAlbumDao {
  /**
   * Insert the artist/album relationship replacing on conflict if the pair already exists
   */
  fun insertArtistAlbum(
    txn: TransactionInProgress,
    newArtistId: ArtistId,
    newAlbumId: AlbumId,
    createTime: Millis
  )

  fun deleteAll(txn: TransactionInProgress)

  companion object {
    operator fun invoke(): ArtistAlbumDao = ArtistAlbumDaoImpl()
  }
}

private val INSERT_ARTIST_ALBUM = ArtistAlbumTable.insertValues(OnConflict.Replace) {
  it[artistId].bindArg()
  it[albumId].bindArg()
  it[createdTime].bindArg()
}

private class ArtistAlbumDaoImpl : ArtistAlbumDao {
  override fun insertArtistAlbum(
    txn: TransactionInProgress,
    newArtistId: ArtistId,
    newAlbumId: AlbumId,
    createTime: Millis
  ) = txn.run {
    INSERT_ARTIST_ALBUM.insert {
      it[artistId] = newArtistId.value
      it[albumId] = newAlbumId.value
      it[createdTime] = createTime()
    }
    Unit
  }

  override fun deleteAll(txn: TransactionInProgress) = txn.run {
    val count = ArtistAlbumTable.deleteAll()
    LOG.i { it("Deleted %d artist/album associations", count) }
  }
}
