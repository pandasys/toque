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
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.asAlbumTitle
import com.ealva.ealvabrainz.common.asArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.EntityArtwork
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.AlbumDaoEvent.AlbumArtworkUpdated
import com.ealva.toque.db.DaoCommon.ESC_CHAR
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asAlbumId
import com.ealva.toque.ui.library.ArtistType
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.compound.CompoundSelect
import com.ealva.welite.db.compound.union
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.isNotNull
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.expr.neq
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.expr.sum
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.countDistinct
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.groupsBy
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.existingView
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
import kotlin.time.Duration

private val LOG by lazyLogger(AlbumDao::class)

sealed interface AlbumDaoEvent {
  data class AlbumCreated(val albumId: AlbumId) : AlbumDaoEvent
  data class AlbumUpdated(val albumId: AlbumId) : AlbumDaoEvent
  data class AlbumArtworkUpdated(
    val albumId: AlbumId,
    val albumArt: Uri,
    val localAlbumArt: Uri
  ) : AlbumDaoEvent
}

data class AlbumDescription(
  val albumId: AlbumId,
  val albumTitle: AlbumTitle,
  val artistName: ArtistName,
  val albumYear: Int,
  override val localArtwork: Uri,
  override val remoteArtwork: Uri,
  val songCount: Long,
  val duration: Duration
) : EntityArtwork

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
  fun TransactionInProgress.upsertAlbum(
    album: String,
    albumSort: String,
    albumArt: Uri,
    albumArtist: String,
    year: Int,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): AlbumId

  fun TransactionInProgress.deleteAll(): Long

  fun TransactionInProgress.deleteAlbumsWithNoMedia(): Long

  suspend fun getAllAlbums(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AlbumDescription>>

  suspend fun getAllAlbumsFor(
    artistId: ArtistId,
    artistType: ArtistType,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AlbumDescription>>

  suspend fun getNext(albumId: AlbumId): DaoResult<AlbumId>
  suspend fun getPrevious(albumId: AlbumId): DaoResult<AlbumId>
  suspend fun getMin(): DaoResult<AlbumId>
  suspend fun getMax(): DaoResult<AlbumId>
  suspend fun getRandom(): DaoResult<AlbumId>

  suspend fun getAlbumSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  suspend fun downloadArt(albumId: AlbumId, block: (AlbumDao) -> Unit)

  fun setAlbumArt(albumId: AlbumId, remote: Uri, local: Uri)

  suspend fun getArtistAlbum(albumId: AlbumId): DaoResult<Pair<ArtistName, AlbumTitle>>

  suspend fun getAlbumArtFor(artistId: ArtistId, artistType: ArtistType): DaoResult<Uri>

  suspend fun getAlbumArtFor(genreId: GenreId): DaoResult<Uri>

  suspend fun getAlbumArtFor(composerId: ComposerId): DaoResult<Uri>

  suspend fun getAlbumArtFor(
    playlistId: PlaylistId,
    playlistType: PlayListType,
    playlistName: PlaylistName
  ): DaoResult<Uri>

  suspend fun getAlbumYear(albumId: AlbumId): Int

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
  override fun TransactionInProgress.upsertAlbum(
    album: String,
    albumSort: String,
    albumArt: Uri,
    albumArtist: String,
    year: Int,
    releaseMbid: ReleaseMbid?,
    releaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): AlbumId = upsertLock.withLock {
    require(album.isNotBlank()) { "Album may not be blank" }
    doUpsertAlbum(
      album,
      albumSort,
      albumArt,
      albumArtist,
      year,
      releaseMbid,
      releaseGroupMbid,
      createUpdateTime,
      upsertResults
    )
  }

  override fun TransactionInProgress.deleteAll(): Long = AlbumTable.deleteAll()

  override fun TransactionInProgress.deleteAlbumsWithNoMedia(): Long = AlbumTable
    .deleteWhere {
      literal(0) eq (MediaTable.selectCount { AlbumTable.id eq albumId }).asExpression()
    }.delete()

  override suspend fun getAllAlbums(
    filter: Filter,
    limit: Limit
  ): Result<List<AlbumDescription>, Throwable> = runSuspendCatching {
    db.query {
      MediaTable
        .join(ArtistTable, JoinType.INNER, MediaTable.albumArtistId, ArtistTable.id)
        .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
        .selects {
          listOf<SqlTypeExpression<out Any>>(
            AlbumTable.id,
            AlbumTable.albumTitle,
            ArtistTable.artistName,
            AlbumTable.albumYear,
            AlbumTable.albumLocalArtUri,
            AlbumTable.albumArtUri,
            songCountColumn,
            totalDurationColumn
          )
        }
        .where { filter.whereCondition() }
        .groupsBy { listOf(AlbumTable.albumTitle, AlbumTable.albumArtist) }
        .orderByAsc { AlbumTable.albumSort }
        .limit(limit.value)
        .sequence { cursor ->
          AlbumDescription(
            cursor[AlbumTable.id].asAlbumId,
            cursor[AlbumTable.albumTitle].asAlbumTitle,
            cursor[ArtistTable.artistName].asArtistName,
            cursor[AlbumTable.albumYear],
            cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
            cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
            cursor[songCountColumn],
            cursor[totalDurationColumn]
          )
        }
        .toList()
    }
  }

  private fun Filter.whereCondition() =
    if (isEmpty) null else (AlbumTable.albumTitle like value escape ESC_CHAR) or
      (ArtistTable.artistName like value escape ESC_CHAR)

  override suspend fun getAllAlbumsFor(
    artistId: ArtistId,
    artistType: ArtistType,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AlbumDescription>> = runSuspendCatching {
    db.query {
      when (artistType) {
        ArtistType.AlbumArtist -> doGetAlbumArtistAlbums(artistId, limit)
        ArtistType.SongArtist -> doGetArtistAlbums(artistId, limit)
      }
    }
  }

  private fun Queryable.doGetAlbumArtistAlbums(artistId: ArtistId, limit: Limit) = ArtistAlbumTable
    .join(AlbumTable, JoinType.INNER, ArtistAlbumTable.albumId, AlbumTable.id)
    .join(ArtistTable, JoinType.INNER, ArtistAlbumTable.artistId, ArtistTable.id)
    .join(MediaTable, JoinType.INNER, AlbumTable.id, MediaTable.albumId)
    .selects {
      listOf(
        AlbumTable.id,
        AlbumTable.albumTitle,
        ArtistTable.artistName,
        AlbumTable.albumYear,
        AlbumTable.albumLocalArtUri,
        AlbumTable.albumArtUri,
        songCountColumn,
        totalDurationColumn
      )
    }
    .where { ArtistAlbumTable.artistId eq artistId.value }
    .groupBy { AlbumTable.albumSort }
    .orderByAsc { AlbumTable.albumSort }
    .limit(limit.value)
    .sequence { cursor ->
      AlbumDescription(
        cursor[AlbumTable.id].asAlbumId,
        cursor[AlbumTable.albumTitle].asAlbumTitle,
        cursor[ArtistTable.artistName].asArtistName,
        cursor[AlbumTable.albumYear],
        cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
        cursor[songCountColumn],
        cursor[totalDurationColumn]
      )
    }
    .toList()

  private fun Queryable.doGetArtistAlbums(artistId: ArtistId?, limit: Limit) = MediaTable
    .join(ArtistTable, JoinType.INNER, MediaTable.albumArtistId, ArtistTable.id)
    .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
    .selects {
      listOf(
        AlbumTable.id,
        AlbumTable.albumTitle,
        ArtistTable.artistName,
        AlbumTable.albumYear,
        AlbumTable.albumLocalArtUri,
        AlbumTable.albumArtUri,
        songCountColumn,
        totalDurationColumn
      )
    }
    .where { if (artistId == null) null else MediaTable.artistId eq artistId.value }
    .groupBy { AlbumTable.albumTitle }
    .orderByAsc { AlbumTable.albumSort }
    .limit(limit.value)
    .sequence { cursor ->
      AlbumDescription(
        cursor[AlbumTable.id].asAlbumId,
        cursor[AlbumTable.albumTitle].asAlbumTitle,
        cursor[ArtistTable.artistName].asArtistName,
        cursor[AlbumTable.albumYear],
        cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
        cursor[songCountColumn],
        cursor[totalDurationColumn]
      )
    }
    .toList()

  override suspend fun getNext(albumId: AlbumId): DaoResult<AlbumId> = runSuspendCatching {
    db.query {
      AlbumTable
        .select { id }
        .where { albumSort greater SELECT_ALBUM_SORT_FROM_BIND_ID }
        .orderByAsc { albumSort }
        .limit(1)
        .longForQuery { it[BIND_ALBUM_ID] = albumId.value }
        .asAlbumId
    }
  }

  override suspend fun getPrevious(albumId: AlbumId): DaoResult<AlbumId> = runSuspendCatching {
    db.query {
      AlbumTable
        .select { id }
        .where { albumSort less SELECT_ALBUM_SORT_FROM_BIND_ID }
        .orderBy { albumSort by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_ALBUM_ID] = albumId.value }
        .asAlbumId
    }
  }

  private val albumMin by lazy { AlbumTable.albumSort.min().alias("album_sort_min_alias") }
  override suspend fun getMin(): DaoResult<AlbumId> = runSuspendCatching {
    db.query {
      AlbumTable
        .selects { listOf(id, albumMin) }
        .all()
        .limit(1)
        .sequence { cursor -> AlbumId(cursor[id]) }
        .single()
    }
  }

  private val albumMax by lazy { AlbumTable.albumSort.max().alias("album_sort_max_alias") }
  override suspend fun getMax(): DaoResult<AlbumId> = runSuspendCatching {
    db.query {
      AlbumTable
        .selects { listOf(id, albumMax) }
        .all()
        .limit(1)
        .sequence { cursor -> AlbumId(cursor[id]) }
        .single()
    }
  }

  override suspend fun getRandom(): DaoResult<AlbumId> = runSuspendCatching {
    db.query {
      AlbumTable
        .select { id }
        .where { id inSubQuery AlbumTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asAlbumId
    }
  }

  override suspend fun getAlbumSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      AlbumTable
        .select { albumTitle }
        .where { albumTitle like textSearch.applyWildcards(partialTitle) escape ESC_CHAR }
        .sequence { it[albumTitle] }
        .toList()
    }
  }

  override suspend fun downloadArt(albumId: AlbumId, block: (AlbumDao) -> Unit) {
    val locked = db.transaction {
      AlbumTable
        .updateColumns { it[downloadingArtwork] = true }
        .where { id eq albumId.value and (downloadingArtwork eq false) }
        .update() > 0
    }
    if (locked) {
      try {
        block(this)
      } catch (e: Throwable) {
        LOG.e(e) { it("Uncaught exception downloading art for %s", albumId) }
        throw e
      } finally {
        val unlocked = db.transaction {
          AlbumTable
            .updateColumns { it[downloadingArtwork] = false }
            .where { id eq albumId.value }
            .update() > 0
        }
        if (!unlocked) LOG.e { it("Failed to set %s as 'not downloading'", albumId) }
      }
    }
  }

  override fun setAlbumArt(albumId: AlbumId, remote: Uri, local: Uri) {
    scope.launch {
      val rows = db.transaction {
        AlbumTable
          .updateColumns {
            it[albumArtUri] = remote.toString()
            it[albumLocalArtUri] = local.toString()
          }
          .where { id eq albumId.value }
          .update()
      }
      if (rows >= 1) emit(AlbumArtworkUpdated(albumId, remote, local)) else LOG.e {
        it("Error updating artwork %s %s %s", albumId, remote, local)
      }
    }
  }

  override suspend fun getArtistAlbum(
    albumId: AlbumId
  ): DaoResult<Pair<ArtistName, AlbumTitle>> = runSuspendCatching {
    db.query {
      AlbumTable
        .join(ArtistAlbumTable, JoinType.INNER, AlbumTable.id, ArtistAlbumTable.albumId)
        .join(ArtistTable, JoinType.INNER, ArtistAlbumTable.artistId, ArtistTable.id)
        .selects { listOf(AlbumTable.albumTitle, ArtistTable.artistName) }
        .where { AlbumTable.id eq albumId.value }
        .sequence { cursor ->
          Pair(
            cursor[ArtistTable.artistName].asArtistName,
            cursor[AlbumTable.albumTitle].asAlbumTitle
          )
        }
        .firstOrNull() ?: throw DaoNotFoundException("Could not find artist/album for $albumId")
    }
  }

  override suspend fun getAlbumArtFor(
    artistId: ArtistId,
    artistType: ArtistType
  ): DaoResult<Uri> = runSuspendCatching {
    db.query {
      if (artistType === ArtistType.AlbumArtist) {
        getArtwork(
          join = ArtistAlbumTable
            .join(AlbumTable, JoinType.INNER, ArtistAlbumTable.albumId, AlbumTable.id),
          where = ArtistAlbumTable.artistId eq artistId.value
        )
      } else {
        getArtwork(
          join = ArtistMediaTable
            .join(MediaTable, JoinType.INNER, ArtistMediaTable.mediaId, MediaTable.id)
            .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id),
          where = ArtistMediaTable.artistId eq artistId.value
        )
      }
    }
  }

  override suspend fun getAlbumArtFor(genreId: GenreId): DaoResult<Uri> = runSuspendCatching {
    db.query {
      getArtwork(
        join = GenreMediaTable
          .join(MediaTable, JoinType.INNER, GenreMediaTable.mediaId, MediaTable.id)
          .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id),
        where = GenreMediaTable.genreId eq genreId.value
      )
    }
  }

  override suspend fun getAlbumArtFor(
    composerId: ComposerId
  ): DaoResult<Uri> = runSuspendCatching {
    db.query {
      getArtwork(
        join = ComposerMediaTable
          .join(MediaTable, JoinType.INNER, ComposerMediaTable.mediaId, MediaTable.id)
          .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id),
        where = ComposerMediaTable.composerId eq composerId.value
      )
    }
  }

  override suspend fun getAlbumArtFor(
    playlistId: PlaylistId,
    playlistType: PlayListType,
    playlistName: PlaylistName
  ): DaoResult<Uri> = runSuspendCatching {
    db.query {
      when (playlistType) {
        PlayListType.UserCreated, PlayListType.File, PlayListType.System -> getArtwork(
          join = PlayListMediaTable
            .join(MediaTable, JoinType.INNER, PlayListMediaTable.mediaId, MediaTable.id)
            .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id),
          where = PlayListMediaTable.playListId eq playlistId.value
        )

        PlayListType.Rules -> {
          val view = existingView(playlistName.value, forceQuoteName = true)
          val viewId = view.column(MediaTable.id, MediaTable.id.name)
          getArtwork(
            join = view.join(MediaTable, JoinType.INNER, viewId, MediaTable.id)
              .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id),
            where = viewId.isNotNull()
          )
        }
      }
    }
  }

  override suspend fun getAlbumYear(albumId: AlbumId): Int = try {
    db.query {
      AlbumTable
        .select(AlbumTable.albumYear)
        .where { id eq albumId.value }
        .longForQuery()
        .toInt()
    }
  } catch (e: Exception) {
    LOG.e(e) { it("Error querying year for %s", albumId) }
    0
  }

  private fun Queryable.getArtwork(
    join: Join,
    where: Op<Boolean>,
    columnAlias: SqlTypeExpression<String> = localArtAlias.aliasOrSelf()
  ): Uri = buildUnion(join, where)
    .select { columnAlias }
    .where { columnAlias neq "" }
    .sequence { cursor -> cursor[columnAlias].toUriOrEmpty() }
    .filterNot { uri -> uri == Uri.EMPTY }
    .firstOrNull() ?: Uri.EMPTY

  private fun buildUnion(join: Join, where: Op<Boolean>): CompoundSelect<Join> = join
    .select { localArtAlias }
    .where(where)
    .distinct() union join
    .select { remoteArtAlias }
    .where(where)
    .distinct()

  private fun TransactionInProgress.doUpsertAlbum(
    newAlbum: String,
    newAlbumSort: String,
    albumArt: Uri,
    newAlbumArtist: String,
    newYear: Int,
    newReleaseMbid: ReleaseMbid?,
    newReleaseGroupMbid: ReleaseGroupMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): AlbumId = try {
    maybeUpdateAlbum(
      newAlbum,
      newAlbumSort,
      newAlbumArtist,
      newYear,
      newReleaseMbid,
      newReleaseGroupMbid,
      createUpdateTime,
      upsertResults
    ) ?: INSERT_STATEMENT.insert {
      it[albumTitle] = newAlbum
      it[albumSort] = newAlbumSort
      it[albumArtist] = newAlbumArtist
      it[albumYear] = newYear
      it[albumLocalArtUri] = albumArt.toString()
      it[releaseMbid] = newReleaseMbid?.value ?: ""
      it[releaseGroupMbid] = newReleaseGroupMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
    }.asAlbumId
      .also { albumId -> upsertResults.alwaysEmit { emit(AlbumDaoEvent.AlbumCreated(albumId)) } }
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
    newAlbumArtist: String,
    newYear: Int,
    newReleaseMbid: ReleaseMbid?,
    newReleaseGroupMbid: ReleaseGroupMbid?,
    newUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): AlbumId? = queryAlbumInfo(newAlbum, newAlbumArtist)?.let { info ->
    // album could match on query yet differ in case, so update if case changes
    val updateAlbum = newAlbum.takeIfSupersedes(info.album)
    val updateAlbumSort = newAlbumSort.takeIfSupersedes(info.albumSort)
    val updateAlbumArtist = newAlbumArtist.takeIfSupersedes(info.albumArtist)
    val updateYear = newYear.takeIfSupersedes(info.albumYear)
    val updateReleaseMbid = newReleaseMbid?.takeIfSupersedes(info.releaseMbid)
    val updateReleaseGroupMbid = newReleaseGroupMbid?.takeIfSupersedes(info.releaseGroupMbid)

    val updateNeeded = anyNotNull(
      updateAlbum,
      updateAlbumSort,
      updateAlbumArtist,
      updateYear,
      updateReleaseMbid,
      updateReleaseGroupMbid
    )
    if (updateNeeded) {
      val updated = AlbumTable
        .updateColumns {
          updateAlbum?.let { update -> it[albumTitle] = update }
          updateAlbumSort?.let { update -> it[albumSort] = update }
          updateAlbumArtist?.let { update -> it[albumArtist] = update }
          updateYear?.let { update -> it[albumYear] = update }
          updateReleaseMbid?.let { update -> it[releaseMbid] = update.value }
          updateReleaseGroupMbid?.let { update -> it[releaseGroupMbid] = update.value }
          it[updatedTime] = newUpdateTime()
        }
        .where { id eq info.id.value }
        .update()

      if (updated >= 1) emitUpdate(info.id) else {
        LOG.e { it("Could not update $info") }
        upsertResults.emitIfMediaCreated { emitUpdate(info.id) }
      }
    } else upsertResults.emitIfMediaCreated { emitUpdate(info.id) }
    info.id
  }

  private fun emitUpdate(albumId: AlbumId) {
    emit(AlbumDaoEvent.AlbumUpdated(albumId))
  }

  private fun Queryable.queryAlbumInfo(
    albumName: String,
    artistName: String
  ): AlbumQueryInfo? = QUERY_ALBUM_INFO
    .sequence(
      { bindings ->
        bindings[0] = albumName
        bindings[1] = artistName
      }
    ) { cursor ->
      AlbumQueryInfo(
        cursor[id].asAlbumId,
        cursor[albumTitle],
        cursor[albumSort],
        cursor[albumArtist],
        cursor[albumYear],
        ReleaseMbid(cursor[releaseMbid]),
        ReleaseGroupMbid(cursor[releaseGroupMbid])
      )
    }.singleOrNull()
}

private val INSERT_STATEMENT = AlbumTable.insertValues {
  it[albumTitle].bindArg()
  it[albumSort].bindArg()
  it[albumArtist].bindArg()
  it[albumYear].bindArg()
  it[albumLocalArtUri].bindArg()
  it[releaseMbid].bindArg()
  it[releaseGroupMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val QUERY_ALBUM_INFO = Query(
  AlbumTable
    .selects {
      listOf(
        id,
        albumTitle,
        albumSort,
        albumArtist,
        albumYear,
        releaseMbid,
        releaseGroupMbid
      )
    }
    .where { (albumTitle eq bindString()) and (albumArtist eq bindString()) }
)

private data class AlbumQueryInfo(
  val id: AlbumId,
  val album: String,
  val albumSort: String,
  val albumArtist: String,
  val albumYear: Int,
  val releaseMbid: ReleaseMbid,
  val releaseGroupMbid: ReleaseGroupMbid,
)

fun AlbumTitle.isEmpty(): Boolean = value.isEmpty()

private val songCountColumn = MediaTable.id.countDistinct()
private val totalDurationColumn = MediaTable.duration.sum()

private val BIND_ALBUM_ID: BindExpression<Long> = bindLong()
private val SELECT_ALBUM_SORT_FROM_BIND_ID: Expression<String> = AlbumTable
  .select { albumSort }
  .where { id eq BIND_ALBUM_ID }
  .asExpression()

private const val ARTWORK_COLUMN_ALIAS = "album_art_alias"
private val localArtAlias by lazy { AlbumTable.albumLocalArtUri.alias(ARTWORK_COLUMN_ALIAS) }
private val remoteArtAlias by lazy { AlbumTable.albumArtUri.alias(ARTWORK_COLUMN_ALIAS) }
