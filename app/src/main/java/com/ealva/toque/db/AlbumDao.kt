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

import android.net.Uri
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.toAlbumTitle
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.toAlbumId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
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

private val LOG by lazyLogger(AlbumDao::class)

sealed class AlbumDaoEvent {
  data class AlbumCreated(val albumId: AlbumId) : AlbumDaoEvent()
  data class AlbumUpdated(val albumId: AlbumId) : AlbumDaoEvent()
}

data class AlbumDescription(
  val albumId: AlbumId,
  val albumTitle: AlbumTitle,
  val albumLocalArt: Uri,
  val albumArt: Uri,
  val artistName: ArtistName
)

data class AlbumIdName(
  val albumId: AlbumId,
  val albumTitle: AlbumTitle
)

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface AlbumDao {
  val albumDaoEvents: SharedFlow<AlbumDaoEvent>

  /**
   * Update the Album if it exists otherwise insert it
   */
  fun upsertAlbum(
    txn: TransactionInProgress,
    album: String,
    albumSort: String,
    albumArt: Uri,
    albumArtistId: ArtistId,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis
  ): AlbumId

  fun deleteAll(txn: TransactionInProgress): Long

  fun deleteAlbumsWithNoMedia(txn: TransactionInProgress): Long

  suspend fun getAllAlbums(limit: Long): Result<List<AlbumDescription>, DaoMessage>

  suspend fun getAllAlbumsFor(
    artistId: ArtistId,
    limit: Long
  ): Result<List<AlbumDescription>, DaoMessage>

  suspend fun getNextAlbum(title: AlbumTitle): Result<AlbumIdName, DaoMessage>
  suspend fun getPreviousAlbum(title: AlbumTitle): Result<AlbumIdName, DaoMessage>
  suspend fun getRandomAlbum(): Result<AlbumIdName, DaoMessage>

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): AlbumDao = AlbumDaoImpl(db, dispatcher ?: Dispatchers.Main)
  }
}

private class AlbumDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : AlbumDao {
  private val upsertLock: Lock = ReentrantLock()
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  override val albumDaoEvents = MutableSharedFlow<AlbumDaoEvent>()

  private fun emit(event: AlbumDaoEvent) {
    scope.launch { albumDaoEvents.emit(event) }
  }

  /**
   * Given the current design of the media scanner there wouldn't be a race condition between
   * query/update/insert as an artist/album are processed in a group. But we'll hold a lock
   * just in case
   */
  override fun upsertAlbum(
    txn: TransactionInProgress,
    album: String,
    albumSort: String,
    albumArt: Uri,
    albumArtistId: ArtistId,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis
  ): AlbumId = upsertLock.withLock {
    require(album.isNotBlank()) { "Album may not be blank" }
    txn.doUpsertAlbum(
      album,
      albumSort,
      albumArtistId,
      albumArt,
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

  override suspend fun getAllAlbums(
    limit: Long
  ): Result<List<AlbumDescription>, DaoMessage> = db.query {
    runCatching { doGetAlbums(limit = limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  override suspend fun getAllAlbumsFor(
    artistId: ArtistId,
    limit: Long
  ): Result<List<AlbumDescription>, DaoMessage> = db.query {
    runCatching { doGetAlbums(artistId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAlbums(
    artistId: ArtistId? = null,
    limit: Long = Long.MAX_VALUE
  ): List<AlbumDescription> = AlbumTable
    .join(ArtistAlbumTable, JoinType.INNER, AlbumTable.id, ArtistAlbumTable.albumId)
    .join(ArtistTable, JoinType.INNER, ArtistAlbumTable.artistId, ArtistTable.id)
    .selects {
      listOf(
        AlbumTable.id,
        AlbumTable.albumTitle,
        AlbumTable.albumLocalArtUri,
        AlbumTable.albumArtUri,
        ArtistTable.artistName
      )
    }
    .where {
      if (artistId != null) (ArtistAlbumTable.artistId eq artistId.value) else {
        null
      }
    }
    .orderByAsc { AlbumTable.albumTitle }
    .limit(limit)
    .sequence {
      AlbumDescription(
        it[AlbumTable.id].toAlbumId(),
        it[AlbumTable.albumTitle].toAlbumTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty(),
        ArtistName(it[ArtistTable.artistName])
      )
    }
    .toList()

  override suspend fun getNextAlbum(
    title: AlbumTitle
  ): Result<AlbumIdName, DaoMessage> = db.query {
    runCatching { doGetNextAlbum(title) }.mapError { DaoExceptionMessage(it) }
  }

  /**
   * Throws NoSuchElementException if there is no album title > greater than [title]
   */
  private fun Queryable.doGetNextAlbum(title: AlbumTitle): AlbumIdName = AlbumTable
    .selects { listOf(id, albumTitle) }
    .where { albumTitle greater title.value }
    .orderByAsc { albumTitle }
    .limit(1)
    .sequence { AlbumIdName(AlbumId(it[id]), AlbumTitle(it[albumTitle])) }
    .single()

  override suspend fun getPreviousAlbum(title: AlbumTitle) = db.query {
    runCatching { if (title.isEmpty()) doGetMaxAlbum() else doGetPreviousAlbum(title) }
      .mapError { DaoExceptionMessage(it) }
  }

  /**
   * Throws NoSuchElementException if there is no album title > greater than [previousTitle]
   */
  private fun Queryable.doGetPreviousAlbum(previousTitle: AlbumTitle): AlbumIdName = AlbumTable
    .selects { listOf(id, albumTitle) }
    .where { albumTitle less previousTitle.value }
    .orderBy { albumTitle by Order.DESC }
    .limit(1)
    .sequence { AlbumIdName(AlbumId(it[id]), AlbumTitle(it[albumTitle])) }
    .single()

  private val albumMax by lazy { AlbumTable.albumTitle.max().alias("album_max_alias") }
  private fun Queryable.doGetMaxAlbum(): AlbumIdName = AlbumTable
    .selects { listOf(id, albumMax) }
    .all()
    .limit(1)
    .sequence { AlbumIdName(AlbumId(it[id]), AlbumTitle(it[albumMax])) }
    .single()

  override suspend fun getRandomAlbum(): Result<AlbumIdName, DaoMessage> = db.query {
    runCatching { doGetRandomAlbum() }.mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetRandomAlbum(): AlbumIdName = AlbumTable
    .selects { listOf(id, albumTitle) }
    .where { id inSubQuery AlbumTable.select(id).all().orderByRandom().limit(1) }
    .sequence { AlbumIdName(AlbumId(it[id]), AlbumTitle(it[albumTitle])) }
    .single()

  private fun TransactionInProgress.doUpsertAlbum(
    newAlbum: String,
    newAlbumSort: String,
    newAlbumArtistId: ArtistId,
    albumArt: Uri,
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
      it[albumTitle] = newAlbum
      it[albumSort] = newAlbumSort
      it[albumArtistId] = newAlbumArtistId.value
      it[albumLocalArtUri] = albumArt.toString()
      it[releaseMbid] = newReleaseMbid?.value ?: ""
      it[releaseGroupMbid] = newReleaseGroupMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
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
    val updateReleaseGroupMbid = info.releaseGroupMbid.updateOrNull { newReleaseGroupMbid }

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
        updateAlbum?.let { update -> it[albumTitle] = update }
        updateAlbumSort?.let { update -> it[albumSort] = update }
        updateReleaseMbid?.let { update -> it[releaseMbid] = update.value }
        updateReleaseGroupMbid?.let { update -> it[releaseGroupMbid] = update.value }
        it[updatedTime] = newUpdateTime()
      }.where { id eq info.id.value }.update()

      if (updated >= 1) emit(AlbumDaoEvent.AlbumUpdated(info.id))
      else LOG.e { it("Could not update $info") }
    }
    info.id
  }

  private fun Queryable.queryAlbumInfo(albumName: String): AlbumInfo? = QUERY_ALBUM_INFO
    .sequence({ it[0] = albumName }) {
      AlbumInfo(
        it[id].toAlbumId(),
        it[albumTitle],
        it[albumSort],
        ReleaseMbid(it[releaseMbid]),
        ReleaseGroupMbid(it[releaseGroupMbid])
      )
    }.singleOrNull()
}

private val INSERT_STATEMENT = AlbumTable.insertValues {
  it[albumTitle].bindArg()
  it[albumSort].bindArg()
  it[albumArtistId].bindArg()
  it[albumLocalArtUri].bindArg()
  it[releaseMbid].bindArg()
  it[releaseGroupMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val QUERY_ALBUM_INFO = Query(
  AlbumTable
    .selects { listOf(id, albumTitle, albumSort, releaseMbid, releaseGroupMbid) }
    .where { albumTitle eq bindString() }
)

private data class AlbumInfo(
  val id: AlbumId,
  val album: String,
  val albumSort: String,
  val releaseMbid: ReleaseMbid,
  val releaseGroupMbid: ReleaseGroupMbid,
)

fun AlbumTitle.isEmpty(): Boolean = value.isEmpty()
