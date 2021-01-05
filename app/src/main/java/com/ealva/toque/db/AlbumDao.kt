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

import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.brainz.data.toReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.toReleaseMbid
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.debugRequire
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

inline class AlbumId(override val id: Long) : PersistentId {
  companion object {
    val INVALID = AlbumId(PersistentId.ID_INVALID)
  }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toAlbumId(): AlbumId {
  debugRequire(PersistentId.isValidId(this)) { "All IDs must be greater than 0 to be valid" }
  return AlbumId(this)
}

@Suppress("NOTHING_TO_INLINE")
inline class AlbumIdList(val idList: LongList) : Iterable<AlbumId> {
  inline val size: Int
    get() = idList.size

  inline operator fun plusAssign(genreId: AlbumId) {
    idList.add(genreId.id)
  }

  inline operator fun get(index: Int): AlbumId = AlbumId(idList.getLong(index))

  companion object {
    operator fun invoke(capacity: Int): AlbumIdList = AlbumIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<AlbumId> = idIterator(idList, ::AlbumId)
}

private val LOG by lazyLogger(AlbumDao::class)

interface AlbumDao {
  /**
   * Update the Album if it exists otherwise insert it
   */
  fun upsertAlbum(
    txn: TransactionInProgress,
    album: String,
    albumSort: String,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis
  ): AlbumId

  fun deleteAll(txn: TransactionInProgress): Long

  fun deleteAlbumsWithNoMedia(txn: TransactionInProgress): Long

  companion object {
    operator fun invoke(): AlbumDao = AlbumDaoImpl()
  }
}

private val upsertLock: Lock = ReentrantLock()

private class AlbumDaoImpl : AlbumDao {
  /**
   * Given the current design of the media scanner there wouldn't be a race condition between
   * query/update/insert as an artist/album are processed in a group. But we'll hold a lock
   * just in case
   */
  override fun upsertAlbum(
    txn: TransactionInProgress,
    album: String,
    albumSort: String,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis
  ): AlbumId = upsertLock.withLock {
    require(album.isNotBlank()) { "Album may not be blank" }
    txn.doUpsertAlbum(
      album,
      albumSort,
      releaseMbid,
      releaseGroupMbid,
      createUpdateTime
    )
  }

  override fun deleteAll(txn: TransactionInProgress): Long = txn.run { AlbumTable.deleteAll() }

  override fun deleteAlbumsWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    AlbumTable.deleteWhere {
      literal(0) eq (MediaTable.selectCount { AlbumTable.id eq albumId }).asExpression()
    }.delete()
  }

  private fun TransactionInProgress.doUpsertAlbum(
    newAlbum: String,
    newAlbumSort: String,
    newReleaseMbid: ReleaseMbid?,
    newReleaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis
  ): AlbumId = try {
    maybeUpdateAlbum(
      newAlbum,
      newAlbumSort,
      newReleaseMbid,
      newReleaseGroupMbid,
      createUpdateTime
    ) ?: INSERT_STATEMENT.insert {
      it[album] = newAlbum
      it[albumSort] = newAlbumSort
      it[releaseMbid] = newReleaseMbid?.value ?: ""
      it[releaseGroupMbid] = newReleaseGroupMbid?.value ?: ""
      it[createdTime] = createUpdateTime.value
      it[updatedTime] = createUpdateTime.value
    }.toAlbumId()
  } catch (e: Exception) {
    LOG.e(e) { it("Exception with album='%s'", newAlbum) }
    throw e
  }

  /**
   * Update the album if necessary and return the AlbumId. Null is returned if the album does not
   * exist
   */
  private fun TransactionInProgress.maybeUpdateAlbum(
    newAlbum: String,
    newAlbumSort: String,
    newReleaseMbid: ReleaseMbid?,
    newReleaseGroupMbid: ReleaseGroupMbid?,
    newUpdateTime: Millis
  ): AlbumId? = queryAlbumInfo(newAlbum)?.let { info ->
    // album could match on query yet differ in case, so update if case changes
    val updateAlbum = info.album.updateOrNull { newAlbum }
    val updateAlbumSort = info.albumSort.updateOrNull { newAlbumSort }
    val updateReleaseMbid = info.releaseMbid.updateOrNull { newReleaseMbid }
    val updateReleaseGroupMbid = info.releaseMbid.updateOrNull { newReleaseGroupMbid }

    val updateNeeded = anyNotNull {
      arrayOf(
        updateAlbum,
        updateAlbumSort,
        updateReleaseMbid,
        updateReleaseGroupMbid
      )
    }
    if (updateNeeded) {
      val updated = AlbumTable.updateColumns {
        updateAlbum?.let { update -> it[album] = update }
        updateAlbumSort?.let { update -> it[albumSort] = update }
        updateReleaseMbid?.let { update -> it[releaseMbid] = update.value }
        updateReleaseGroupMbid?.let { update -> it[releaseGroupMbid] = update.value }
        it[updatedTime] = newUpdateTime.value
      }.where { id eq info.id.id }.update()

      if (updated < 1) LOG.e { it("Could not update $info") }
    }
    info.id
  }

  private fun Queryable.queryAlbumInfo(albumName: String): AlbumInfo? = QUERY_ALBUM_INFO
    .sequence({ it[0] = albumName }) {
      AlbumInfo(
        it[id].toAlbumId(),
        it[album],
        it[albumSort],
        it[releaseMbid].toReleaseMbid(),
        it[releaseGroupMbid].toReleaseGroupMbid()
      )
    }.singleOrNull()
}

private val INSERT_STATEMENT = AlbumTable.insertValues {
  it[album].bindArg()
  it[albumSort].bindArg()
  it[releaseMbid].bindArg()
  it[releaseGroupMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val QUERY_ALBUM_INFO = Query(
  AlbumTable
    .selects { listOf(id, album, albumSort, releaseMbid, releaseGroupMbid) }
    .where { album eq bindString() }
)

private data class AlbumInfo(
  val id: AlbumId,
  val album: String,
  val albumSort: String,
  val releaseMbid: ReleaseMbid,
  val releaseGroupMbid: ReleaseGroupMbid,
)
