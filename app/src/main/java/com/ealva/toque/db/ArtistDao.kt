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

import android.net.Uri
import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.DaoCommon.ESC_CHAR
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.asArtistId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.compound.union
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.countDistinct
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
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

data class ArtistDescription(
  val artistId: ArtistId,
  val name: ArtistName,
  val artwork: Uri,
  val albumCount: Long,
  val songCount: Long
)

data class ArtistIdName(val artistId: ArtistId, val artistName: ArtistName)

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
  fun TransactionInProgress.upsertArtist(
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId

  fun deleteAll(txn: TransactionInProgress): Long
  fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long
  suspend fun getAlbumArtists(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<ArtistDescription>>

  suspend fun getSongArtists(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<ArtistDescription>>

  suspend fun getAllArtistNames(limit: Limit = NoLimit): DaoResult<List<ArtistIdName>>

  suspend fun getNext(id: ArtistId): DaoResult<ArtistId>
  suspend fun getPrevious(id: ArtistId): DaoResult<ArtistId>
  suspend fun getMin(): DaoResult<ArtistId>
  suspend fun getMax(): DaoResult<ArtistId>
  suspend fun getRandom(): DaoResult<ArtistId>

  suspend fun getArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  suspend fun getAlbumArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  fun TransactionInProgress.replaceMediaArtists(
    artistIdList: ArtistIdList,
    replaceMediaId: MediaId,
    createTime: Millis
  )

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): ArtistDao = ArtistDaoImpl(db, dispatcher ?: Dispatchers.Main)

    val AlbumArtistTable = ArtistTable.alias("AlbumArtist")
    val SongArtistTable = ArtistTable.alias("SongArtist")

    val albumArtistTableId = AlbumArtistTable[ArtistTable.id]
    val albumArtistTableName = AlbumArtistTable[ArtistTable.artistName]
    val songArtistTableId = SongArtistTable[ArtistTable.id]
    val songArtistTableName = SongArtistTable[ArtistTable.artistName]
  }
}

private class ArtistDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : ArtistDao {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val artistMediaDao = ArtistMediaDao()
  private val upsertLock: Lock = ReentrantLock()

  override val artistDaoEvents = MutableSharedFlow<ArtistDaoEvent>()

  private fun emit(event: ArtistDaoEvent) {
    scope.launch { artistDaoEvents.emit(event) }
  }

  override fun TransactionInProgress.upsertArtist(
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId = upsertLock.withLock {
    require(artistName.isNotBlank()) { "Artist may not be blank" }
    doUpsertArtist(artistName, artistSort, artistMbid, createUpdateTime, upsertResults)
  }

  private fun TransactionInProgress.doUpsertArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId = try {
    maybeUpdateArtist(
      newArtist,
      newArtistSort,
      newArtistMbid,
      createUpdateTime,
      upsertResults
    ) ?: INSERT_STATEMENT.insert {
      it[artistName] = newArtist
      it[artistSort] = newArtistSort
      it[artistMbid] = newArtistMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
    }
      .asArtistId
      .also { id -> upsertResults.alwaysEmit { emit(ArtistDaoEvent.ArtistCreated(id)) } }
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
    newUpdateTime: Millis,
    upsertResults: AudioUpsertResults
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
        it[updatedTime] = newUpdateTime()
      }.where { id eq info.id.value }.update()

      if (updated >= 1) upsertResults.alwaysEmit { emitUpdated(info.id) } else {
        LOG.e { it("Could not update $info") }
        upsertResults.emitIfMediaCreated { emitUpdated(info.id) }
      }
    } else upsertResults.emitIfMediaCreated { emitUpdated(info.id) }

    info.id
  }

  private fun emitUpdated(artistId: ArtistId) {
    emit(ArtistDaoEvent.ArtistUpdated(artistId))
  }

  private fun Queryable.queryArtistUpdateInfo(
    artistName: String
  ): ArtistUpdateInfo? = QUERY_ARTIST_UPDATE_INFO
    .sequence({ bindings -> bindings[queryArtistBind] = artistName }) { cursor ->
      ArtistUpdateInfo(
        cursor[id].asArtistId,
        cursor[this.artistName],
        cursor[artistSort],
        ArtistMbid(cursor[artistMbid])
      )
    }.singleOrNull()

  override fun deleteAll(txn: TransactionInProgress): Long = txn.run {
    ArtistTable.deleteAll()
  }

  override fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    DELETE_ARTISTS_WITH_NO_MEDIA.delete()
  }

  override suspend fun getAlbumArtists(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ArtistDescription>> = runSuspendCatching {
    db.query { doGetAlbumArtists(filter, limit) }
  }

  private fun Queryable.doGetAlbumArtists(filter: Filter, limit: Limit): List<ArtistDescription> {
    return ArtistTable
      .join(ArtistAlbumTable, JoinType.INNER, ArtistTable.id, ArtistAlbumTable.artistId)
      .join(AlbumTable, JoinType.INNER, ArtistAlbumTable.albumId, AlbumTable.id)
      .join(MediaTable, JoinType.INNER, ArtistTable.id, MediaTable.albumArtistId)
      .selects {
        listOf(
          ArtistTable.id,
          ArtistTable.artistName,
          ArtistTable.artistImage,
          songCountColumn,
          albumArtistAlbumCountColumn
        )
      }
      .where { filter.whereCondition() }
      .groupBy { ArtistTable.artistSort }
      .orderByAsc { ArtistTable.artistSort }
      .limit(limit.value)
      .sequence { cursor ->
        ArtistDescription(
          ArtistId(cursor[ArtistTable.id]),
          ArtistName(cursor[ArtistTable.artistName]),
          cursor[ArtistTable.artistImage].toUriOrEmpty(),
          cursor[albumArtistAlbumCountColumn],
          cursor[songCountColumn]
        )
      }
      .toList()
  }

  override suspend fun getSongArtists(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ArtistDescription>> = runSuspendCatching {
    db.query { doGetSongArtists(filter, limit) }
  }

  private fun Queryable.doGetSongArtists(filter: Filter, limit: Limit): List<ArtistDescription> {
    return ArtistTable
      .join(ArtistMediaTable, JoinType.INNER, ArtistTable.id, ArtistMediaTable.artistId)
      .join(MediaTable, JoinType.INNER, ArtistMediaTable.mediaId, MediaTable.id)
      .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
      .selects {
        listOf(
          ArtistTable.id,
          ArtistTable.artistName,
          ArtistTable.artistImage,
          songCountColumn,
          songArtistAlbumCountColumn
        )
      }
      .where { filter.whereCondition() }
      .groupBy { ArtistTable.artistSort }
      .orderByAsc { ArtistTable.artistSort }
      .limit(limit.value)
      .sequence { cursor ->
        ArtistDescription(
          ArtistId(cursor[ArtistTable.id]),
          ArtistName(cursor[ArtistTable.artistName]),
          cursor[ArtistTable.artistImage].toUriOrEmpty(),
          cursor[songArtistAlbumCountColumn],
          cursor[songCountColumn]
        )
      }
      .toList()
  }

  private fun Filter.whereCondition() =
    if (isEmpty) null else ArtistTable.artistName like value escape ESC_CHAR

  override suspend fun getAllArtistNames(
    limit: Limit
  ): DaoResult<List<ArtistIdName>> = runSuspendCatching {
    db.query {
      ArtistTable
        .selects { listOf(id, artistName) }
        .all()
        .orderByAsc { artistSort }
        .limit(limit.value)
        .sequence { cursor -> ArtistIdName(ArtistId(cursor[id]), ArtistName(cursor[artistName])) }
        .toList()
    }
  }

  override suspend fun getNext(id: ArtistId): DaoResult<ArtistId> = runSuspendCatching {
    db.query {
      ArtistTable
        .select(ArtistTable.id)
        .where { artistSort greater SELECT_ARTIST_SORT_FROM_BIND_ID }
        .orderByAsc { artistSort }
        .limit(1)
        .longForQuery { it[BIND_ARTIST_ID] = id.value }
        .asArtistId
    }
  }

  override suspend fun getPrevious(id: ArtistId): DaoResult<ArtistId> = runSuspendCatching {
    db.query {
      ArtistTable
        .select(ArtistTable.id)
        .where { artistSort less SELECT_ARTIST_SORT_FROM_BIND_ID }
        .orderBy { artistSort by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_ARTIST_ID] = id.value }
        .asArtistId
    }
  }

  override suspend fun getMin(): DaoResult<ArtistId> = runSuspendCatching {
    db.query {
      ArtistTable
        .selects { listOf(id, artistSortMin) }
        .all()
        .limit(1)
        .sequence { cursor -> ArtistId(cursor[id]) }
        .single()
    }
  }

  override suspend fun getMax(): DaoResult<ArtistId> = runSuspendCatching {
    db.query {
      ArtistTable
        .selects { listOf(id, artistSortMax) }
        .all()
        .limit(1)
        .sequence { cursor -> ArtistId(cursor[id]) }
        .single()
    }
  }

  override suspend fun getRandom(): DaoResult<ArtistId> = runSuspendCatching {
    db.query {
      ArtistTable
        .select(ArtistTable.id)
        .where { id inSubQuery ArtistTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asArtistId
    }
  }

  override suspend fun getArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      ArtistDao.SongArtistTable
        .join(MediaTable, JoinType.INNER, ArtistDao.songArtistTableId, MediaTable.artistId)
        .select { ArtistDao.songArtistTableName }
        .where { ArtistDao.songArtistTableName like textSearch.applyWildcards(partial) escape ESC_CHAR }
        .distinct()
        .sequence { it[ArtistDao.songArtistTableName] }
        .toList()
    }
  }

  override suspend fun getAlbumArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      ArtistDao.AlbumArtistTable
        .join(MediaTable, JoinType.INNER, ArtistDao.albumArtistTableId, MediaTable.albumArtistId)
        .select { ArtistDao.albumArtistTableName }
        .where { ArtistDao.albumArtistTableName like textSearch.applyWildcards(partial) escape ESC_CHAR }
        .distinct()
        .sequence { it[ArtistDao.albumArtistTableName] }
        .toList()
    }
  }

  override fun TransactionInProgress.replaceMediaArtists(
    artistIdList: ArtistIdList,
    replaceMediaId: MediaId,
    createTime: Millis
  ) = with(artistMediaDao) { replaceMediaArtists(artistIdList, replaceMediaId, createTime) }
}

private val songCountColumn = MediaTable.id.countDistinct()
private val songArtistAlbumCountColumn = AlbumTable.id.countDistinct()
private val albumArtistAlbumCountColumn = ArtistAlbumTable.albumId.countDistinct()
private val artistSortMax by lazy { ArtistTable.artistSort.max().alias("artist_sort_max_alias") }
private val artistSortMin by lazy { ArtistTable.artistSort.min().alias("artist_sort_min_alias") }

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

fun ArtistName.isEmpty(): Boolean = value.isEmpty()

private val BIND_ARTIST_ID: BindExpression<Long> = bindLong()
private val SELECT_ARTIST_SORT_FROM_BIND_ID: Expression<String> = ArtistTable
  .select { artistSort }
  .where { id eq BIND_ARTIST_ID }
  .limit(1)
  .asExpression()
