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
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.toArtistId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.compound.union
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
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

private val LOG by lazyLogger(ArtistDao::class)

data class ArtistIdName(val artistId: ArtistId, val artistName: String)

sealed class ArtistDaoEvent {
  data class ArtistCreated(val artistId: ArtistId) : ArtistDaoEvent()
  data class ArtistUpdated(val artistId: ArtistId) : ArtistDaoEvent()
}

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface ArtistDao {
  val artistDaoEvents: SharedFlow<ArtistDaoEvent>

  /**
   * Update the artist info if it exists otherwise insert it
   */
  fun upsertArtist(
    txn: TransactionInProgress,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis
  ): ArtistId

  fun deleteAll(txn: TransactionInProgress): Long
  fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long

  suspend fun getAllArtists(limit: Long): Result<List<ArtistIdName>, DaoMessage>

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): ArtistDao = ArtistDaoImpl(db, dispatcher ?: Dispatchers.Main)
  }
}

private class ArtistDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : ArtistDao {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val upsertLock: Lock = ReentrantLock()

  override val artistDaoEvents = MutableSharedFlow<ArtistDaoEvent>()

  private fun emit(event: ArtistDaoEvent) {
    scope.launch { artistDaoEvents.emit(event) }
  }

  override fun upsertArtist(
    txn: TransactionInProgress,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis
  ): ArtistId = upsertLock.withLock {
    require(artistName.isNotBlank()) { "Artist may not be blank" }
    txn.doUpsertArtist(artistName, artistSort, artistMbid, createUpdateTime)
  }

  private fun TransactionInProgress.doUpsertArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    createUpdateTime: Millis
  ): ArtistId = try {
    maybeUpdateArtist(
      newArtist,
      newArtistSort,
      newArtistMbid,
      createUpdateTime
    ) ?: INSERT_STATEMENT.insert {
      it[artistName] = newArtist
      it[artistSort] = newArtistSort
      it[artistMbid] = newArtistMbid?.value ?: ""
      it[createdTime] = createUpdateTime.value
      it[updatedTime] = createUpdateTime.value
    }.toArtistId().also { id -> onCommit { emit(ArtistDaoEvent.ArtistCreated(id)) } }
  } catch (e: Exception) {
    LOG.e(e) { it("Exception with artist='%s'", newArtist) }
    throw e
  }

  /**
   * Update the artist if necessary and return the ArtistId. Null is returned if the artist does not
   * exist
   */
  private fun TransactionInProgress.maybeUpdateArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    newUpdateTime: Millis
  ): ArtistId? = queryArtistUpdateInfo(newArtist)?.let { info ->
    // artist could match on query yet differ in case, so update if case changes
    val updateArtist = info.artist.updateOrNull { newArtist }
    val updateSort = info.artistSort.updateOrNull { newArtistSort }
    val updateMbid = info.artistMbid.updateOrNull { newArtistMbid }

    val updateNeeded = anyNotNull {
      arrayOf(updateArtist, updateSort, updateMbid)
    }

    if (updateNeeded) {
      val updated = ArtistTable.updateColumns {
        updateArtist?.let { update -> it[artistName] = update }
        updateSort?.let { update -> it[artistSort] = update }
        updateMbid?.let { update -> it[artistMbid] = update.value }
        it[updatedTime] = newUpdateTime.value
      }.where { id eq info.id.value }.update()

      if (updated >= 1) onCommit { emit(ArtistDaoEvent.ArtistUpdated(info.id)) }
      else LOG.e { it("Could not update $info") }
    }
    info.id
  }

  private fun Queryable.queryArtistUpdateInfo(
    artistName: String
  ): ArtistUpdateInfo? = QUERY_ARTIST_UPDATE_INFO
    .sequence({ it[queryArtistBind] = artistName }) {
      ArtistUpdateInfo(
        it[id].toArtistId(),
        it[this.artistName],
        it[artistSort],
        ArtistMbid(it[artistMbid])
      )
    }.singleOrNull()

  override fun deleteAll(txn: TransactionInProgress): Long = txn.run {
    ArtistTable.deleteAll()
  }

  override fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    DELETE_ARTISTS_WITH_NO_MEDIA.delete()
  }

  override suspend fun getAllArtists(
    limit: Long
  ): Result<List<ArtistIdName>, DaoMessage> = db.query {
    runCatching { doGetArtistNames(limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetArtistNames(limit: Long): List<ArtistIdName> = ArtistTable
    .selects { listOf(id, artistName) }
    .all()
    .orderByAsc { artistName }
    .limit(limit)
    .sequence { ArtistIdName(it[id].toArtistId(), it[artistName]) }
    .toList()
}

private val INSERT_STATEMENT = ArtistTable.insertValues {
  it[artistName].bindArg()
  it[artistSort].bindArg()
  it[artistMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val queryArtistBind = bindString()

private val QUERY_ARTIST_UPDATE_INFO = Query(
  ArtistTable
    .selects { listOf(id, artistName, artistSort, artistMbid) }
    .where { artistName eq queryArtistBind }
)

private data class ArtistUpdateInfo(
  val id: ArtistId,
  val artist: String,
  val artistSort: String,
  val artistMbid: ArtistMbid
)

private val DELETE_ARTISTS_WITH_NO_MEDIA: DeleteStatement<ArtistTable> = ArtistTable.deleteWhere {
  literal(0L) eq (
    ArtistMediaTable.select { mediaId }.where { artistId eq id } union
      MediaTable.select { id }.where {
        (artistId eq ArtistTable.id) or (albumArtistId eq ArtistTable.id)
      }
    ).selectCount().asExpression()
}
