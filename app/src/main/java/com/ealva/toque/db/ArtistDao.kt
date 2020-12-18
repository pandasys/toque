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

import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.brainz.data.toArtistMbid
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.debugRequire
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.union
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

inline class ArtistId(override val id: Long) : PersistentId

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toArtistId(): ArtistId {
  debugRequire(PersistentId.isValidId(this)) { "All IDs must be greater than 0 to be valid" }
  return ArtistId(this)
}

@Suppress("NOTHING_TO_INLINE")
inline class ArtistIdList(val idList: LongList) : Iterable<ArtistId> {
  inline val size: Int
    get() = idList.size

  inline operator fun plusAssign(artistId: ArtistId) {
    idList.add(artistId.id)
  }

  inline operator fun get(index: Int): ArtistId = ArtistId(idList.getLong(index))

  companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    const val DEFAULT_INITIAL_CAPACITY = 16
    operator fun invoke(capacity: Int = DEFAULT_INITIAL_CAPACITY): ArtistIdList =
      ArtistIdList(LongArrayList(capacity))
  }

  override fun iterator(): Iterator<ArtistId> = idIterator(idList, ::ArtistId)
}

private val LOG by lazyLogger(ArtistDao::class)

interface ArtistDao {
  /**
   * Update the artist info if it exists otherwise insert it
   */
  fun upsertArtist(
    txn: Transaction,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Long
  ): ArtistId

  fun deleteAll(txn: Transaction): Long
  fun deleteArtistsWithNoMedia(txn: Transaction): Long

  companion object {
    operator fun invoke(): ArtistDao = ArtistDaoImpl()
  }
}

private val INSERT_STATEMENT = ArtistTable.insertValues {
  it[artist].bindArg()
  it[artistSort].bindArg()
  it[artistMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val queryArtistBind = bindString()

private val QUERY_ARTIST_INFO: Query = Query(
  ArtistTable.select(
    ArtistTable.id,
    ArtistTable.artist,
    ArtistTable.artistSort,
    ArtistTable.artistMbid
  ).where { ArtistTable.artist eq queryArtistBind }
)

private data class ArtistInfo(
  val id: ArtistId,
  val artist: String,
  val artistSort: String,
  val artistMbid: ArtistMbid
)

private val upsertLock: Lock = ReentrantLock()

private class ArtistDaoImpl : ArtistDao {

  override fun upsertArtist(
    txn: Transaction,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Long
  ): ArtistId = upsertLock.withLock {
    require(artistName.isNotBlank()) { "Artist may not be blank" }
    txn.doUpsertArtist(artistName, artistSort, artistMbid, createUpdateTime)
  }

  override fun deleteAll(txn: Transaction): Long = txn.run {
    ArtistTable.deleteAll()
  }

  override fun deleteArtistsWithNoMedia(txn: Transaction): Long = txn.run {
    DELETE_ARTISTS_WITH_NO_MEDIA.delete()
  }

  private fun Transaction.doUpsertArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    createUpdateTime: Long
  ): ArtistId = try {
    maybeUpdateArtist(
      newArtist,
      newArtistSort,
      newArtistMbid,
      createUpdateTime
    ) ?: INSERT_STATEMENT.insert {
      it[artist] = newArtist
      it[artistSort] = newArtistSort
      it[artistMbid] = newArtistMbid?.value ?: ""
      it[createdTime] = createUpdateTime
      it[updatedTime] = createUpdateTime
    }.toArtistId()
  } catch (e: Exception) {
    LOG.e(e) { it("Exception with artist='%s'", newArtist) }
    throw e
  }

  /**
   * Update the artist if necessary and return the ArtistId. Null is returned if the artist does not
   * exist
   */
  private fun Transaction.maybeUpdateArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    newUpdateTime: Long
  ): ArtistId? = queryArtistInfo(newArtist)?.let { info ->
    // artist could match on query yet differ in case, so update if case changes
    val updateArtist = info.artist.updateOrNull { newArtist }
    val updateSort = info.artistSort.updateOrNull { newArtistSort }
    val updateMbid = info.artistMbid.updateOrNull { newArtistMbid }

    val updateNeeded = anyNotNull {
      arrayOf(updateArtist, updateSort, updateMbid)
    }

    if (updateNeeded) {
      val updated = ArtistTable.updateColumns {
        updateArtist?.let { update -> it[artist] = update }
        updateSort?.let { update -> it[artistSort] = update }
        updateMbid?.let { update -> it[artistMbid] = update.value }
        it[updatedTime] = newUpdateTime
      }.where { id eq info.id.id }.update()

      if (updated < 1) LOG.e { it("Could not update $info") }
    }
    info.id
  }

  private fun Queryable.queryArtistInfo(artist: String): ArtistInfo? = QUERY_ARTIST_INFO
    .sequence({ it[queryArtistBind] = artist }) {
      ArtistInfo(
        it[ArtistTable.id].toArtistId(),
        it[ArtistTable.artist],
        it[ArtistTable.artistSort],
        it[ArtistTable.artistMbid].toArtistMbid()
      )
    }.singleOrNull()
}

val DELETE_ARTISTS_WITH_NO_MEDIA: DeleteStatement<ArtistTable> = ArtistTable.deleteWhere {
  (
    ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
      ArtistMediaTable.artistId eq ArtistTable.id
    } union MediaTable.select(MediaTable.id).where {
      (MediaTable.artistId eq ArtistTable.id) or (MediaTable.albumArtistId eq ArtistTable.id)
    }
    ).selectCount().asExpression<Long>() eq literal(0L)
}
