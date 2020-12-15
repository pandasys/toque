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
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict
import com.ealva.toque.db.ArtistAlbumTable as Table

private val LOG by lazyLogger(ArtistAlbumDao::class)

interface ArtistAlbumDao {
  /**
   * Insert the artist/album relationship ignoring conflict if the pair already exists
   */
  fun insertArtistAlbum(
    txn: Transaction,
    artistId: ArtistId,
    albumId: AlbumId,
    createTime: Long
  )

  fun deleteAll(txn: Transaction)

  companion object {
    operator fun invoke(): ArtistAlbumDao = ArtistAlbumDaoImpl()
  }
}

private var INSERT_ARTIST = -1
private var INSERT_ALBUM = -1
private var INSERT_CREATE_TIME = -1
private val INSERT_ARTIST_ALBUM = Table.insertValues(OnConflict.Replace) {
  it[artistId].bindArg()
  it[albumId].bindArg()
  it[createdTime].bindArg()
  INSERT_ARTIST = it.indexOf(artistId)
  INSERT_ALBUM = it.indexOf(albumId)
  INSERT_CREATE_TIME = it.indexOf(createdTime)
}

private class ArtistAlbumDaoImpl : ArtistAlbumDao {
  override fun insertArtistAlbum(
    txn: Transaction,
    artistId: ArtistId,
    albumId: AlbumId,
    createTime: Long
  ) = txn.run {
    INSERT_ARTIST_ALBUM.insert {
      it[INSERT_ARTIST] = artistId.id
      it[INSERT_ALBUM] = albumId.id
      it[INSERT_CREATE_TIME] = createTime
    }
    Unit
  }

  override fun deleteAll(txn: Transaction) = txn.run {
    val count = Table.deleteAll()
    LOG.i { it("Deleted %d artist/album associations", count) }
  }
}
