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

@file:Suppress("RemoveRedundantQualifierName")

package com.ealva.toque.db

import android.net.Uri
import androidx.core.net.toUri
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.ealvabrainz.brainz.data.isValidMbid
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.toAlbumTitle
import com.ealva.ealvabrainz.common.toArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toTitle
import com.ealva.toque.db.AudioMediaDao.Companion.QUEUE_ID
import com.ealva.toque.db.MediaTable.albumId
import com.ealva.toque.db.MediaTable.id
import com.ealva.toque.db.MediaTable.location
import com.ealva.toque.db.MediaTable.title
import com.ealva.toque.db.MediaTable.trackNumber
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.toAlbumId
import com.ealva.toque.persist.toArtistId
import com.ealva.toque.persist.toMediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.toque.service.media.MediaFormat
import com.ealva.toque.service.media.MediaMetadataParser
import com.ealva.toque.service.media.MediaType
import com.ealva.toque.service.media.Rating
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toRating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.tag.ArtistParserFactory
import com.ealva.toque.tag.SongTag
import com.ealva.toque.tag.toArtistSort
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.expr.plus
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType.INNER
import com.ealva.welite.db.table.JoinType.LEFT
import com.ealva.welite.db.table.OrderBy
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.ordersBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.View
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val LOG by lazyLogger(AudioMediaDao::class)

sealed interface AudioDaoEvent {
  data class MediaCreated(val mediaIds: MediaIdList) : AudioDaoEvent
  data class MediaUpdated(val mediaIds: MediaIdList) : AudioDaoEvent
}

data class AudioDescription(
  val mediaId: MediaId,
  val title: Title,
  val albumLocalArt: Uri,
  val albumArt: Uri
)

// data class FullAudioDescription(
//  val mediaId: MediaId,
//  val title: Title,
//  val albumArtist: ArtistName,
//  val songArtist: ArtistName,
//  val generes: Set<GenreName>,
//  val composer: ComposerName,
//  val albumLocalArt: Uri,
//  val albumArt: Uri
// )

data class AudioQueueItemData(
  val id: MediaId,
  val location: Uri,
  val title: Title,
  val albumTitle: AlbumTitle,
  val albumId: AlbumId,
  val albumArtist: ArtistName,
  val artists: Set<ArtistName>,
  val rating: Rating,
  val duration: Millis,
  val trackNumber: Int,
  val localAlbumArt: Uri,
  val albumArt: Uri
)

typealias AudioDescriptionResult = Result<List<AudioDescription>, DaoMessage>
typealias AudioItemListResult = Result<List<AudioQueueItemData>, DaoMessage>

/**
 * This is the primary entry point for the media scanner to persist media information, via the
 * upsertAudioList.
 */
interface AudioMediaDao {
  val audioDaoEvents: Flow<AudioDaoEvent>

  /**
   * Insert or update a list of audio media. This function begins a transaction which will
   * dispatcher on another thread (except in tests). This is the method the media scanner calls
   * to create or update audio media information.
   */
  suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    metadataParser: MediaMetadataParser,
    minimumDuration: Millis,
    createUpdateTime: Millis
  )

  /**
   * Delete all audio media and related entities, including cascading into relationships
   */
  suspend fun deleteAll()

  /**
   * Delete all audio related entities which no longer have relationships with audio media
   */
  suspend fun deleteEntitiesWithNoMedia()

  suspend fun getCountAllAudio(): LongResult

  suspend fun getAllAudio(limit: Long): AudioDescriptionResult

  suspend fun getAllAudioFor(artistId: ArtistId, limit: Long): AudioDescriptionResult

  suspend fun getAllAudioFor(albumId: AlbumId, limit: Long): AudioDescriptionResult

  suspend fun getAllAudioFor(genreId: GenreId, limit: Long): AudioDescriptionResult

  suspend fun getAllAudioFor(composerId: ComposerId, limit: Long): AudioDescriptionResult

  suspend fun getAudioQueueItems(shuffled: Boolean): AudioItemListResult

  suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T>

  /**
   * Makes a list of AudioQueueItemsData returned in the same order as [idList]
   */
  suspend fun getAudioItemsForQueue(idList: LongList): AudioItemListResult

  suspend fun getNextAlbumList(albumTitle: AlbumTitle): Result<AudioIdList, DaoMessage>

  suspend fun getNextArtistList(artistName: ArtistName): Result<AudioIdList, DaoMessage>

  suspend fun getNextComposerList(composerName: ComposerName): Result<AudioIdList, DaoMessage>

  fun incrementPlayedCount(id: MediaId)

  fun incrementSkippedCount(id: MediaId)

  fun updateDuration(id: MediaId, newDuration: Millis)

  suspend fun setRating(id: MediaId, newRating: Rating): Rating

  suspend fun getMediaTitle(mediaId: MediaId): Result<Title, DaoMessage>

  companion object {
    private const val LOCAL_AUDIO_QUEUE_ID = 1
    val QUEUE_ID = QueueId(LOCAL_AUDIO_QUEUE_ID)

    operator fun invoke(
      db: Database,
      artistParserFactory: ArtistParserFactory,
      genreDao: GenreDao,
      artistDao: ArtistDao,
      albumDao: AlbumDao,
      artistAlbumDao: ArtistAlbumDao,
      composerDao: ComposerDao,
      artistMediaDao: ArtistMediaDao,
      genreMediaDao: GenreMediaDao,
      composerMediaDao: ComposerMediaDao,
      eqPresetAssociationDao: EqPresetAssociationDao,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AudioMediaDao {
      return AudioMediaDaoImpl(
        db,
        artistParserFactory,
        genreDao,
        artistDao,
        albumDao,
        artistAlbumDao,
        composerDao,
        artistMediaDao,
        genreMediaDao,
        composerMediaDao,
        eqPresetAssociationDao,
        dispatcher
      )
    }
  }
}

private val QUERY_AUDIO_FOR_UPDATE = MediaTable.selects {
  listOf(
    id,
    title,
    titleSort,
    albumId,
    albumArtistId,
    artistId,
    year,
    rating,
    duration,
    trackNumber,
    totalTracks,
    discNumber,
    totalDiscs,
    comment,
    trackMbid
  )
}.where { location eq bindString() }

private const val DEFAULT_MAP_SIZE = 1024

private const val CANT_UPDATE_DURATION = "Unable to update duration=%s for media=%s"

@Suppress("LargeClass")
private class AudioMediaDaoImpl(
  private val db: Database,
  private val artistParserFactory: ArtistParserFactory,
  private val genreDao: GenreDao,
  private val artistDao: ArtistDao,
  private val albumDao: AlbumDao,
  private val artistAlbumDao: ArtistAlbumDao,
  private val composerDao: ComposerDao,
  private val artistMediaDao: ArtistMediaDao,
  private val genreMediaDao: GenreMediaDao,
  private val composerMediaDao: ComposerMediaDao,
  private val eqPresetAssocDao: EqPresetAssociationDao,
  dispatcher: CoroutineDispatcher
) : AudioMediaDao {
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

  override val audioDaoEvents = MutableSharedFlow<AudioDaoEvent>(extraBufferCapacity = 5)

  private fun emit(event: AudioDaoEvent) {
    scope.launch { audioDaoEvents.emit(event) }
  }

  /**
   * Parse the file tags and create/update all media relationships. Usually the list would be of one
   * album/artist. There is no contract that the list is only one album/artist or that every track
   * on the album is included.
   */
  override suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    metadataParser: MediaMetadataParser,
    minimumDuration: Millis,
    createUpdateTime: Millis
  ) {
    val artistParser = artistParserFactory.make()
    db.transaction {
      val upsertResults = UpsertResults(audioInfoList.size)
      onCommit { emitResults(upsertResults) }
      audioInfoList.forEach { audioInfo ->
        metadataParser.parseMetadata(audioInfo.uriToParse(), artistParser).use { fileTagInfo ->
          upsertAudio(audioInfo, fileTagInfo, minimumDuration, createUpdateTime, upsertResults)
        }
      }
    }
  }

  private fun Transaction.upsertAudio(
    audioInfo: AudioInfo,
    fileTagInfo: MediaFileTagInfo,
    minimumDuration: Millis,
    createUpdateTime: Millis,
    upsertResults: UpsertResults
  ) {
    val duration = fileTagInfo.duration
    if (duration > minimumDuration) {
      val albumArtistId = upsertAlbumArtist(fileTagInfo, createUpdateTime)
      val albumId = upsertAlbum(
        fileTagInfo,
        audioInfo.albumArt,
        albumArtistId,
        createUpdateTime
      )
      artistAlbumDao.insertArtistAlbum(this, albumArtistId, albumId, createUpdateTime)
      val mediaArtistsIds = upsertMediaArtists(fileTagInfo, createUpdateTime)
      val mediaId = updateOrInsertAudioMedia(
        audioInfo,
        albumId,
        albumArtistId,
        mediaArtistsIds.first(),
        fileTagInfo,
        createUpdateTime,
        upsertResults
      )
      artistMediaDao.replaceMediaArtists(this, mediaArtistsIds, mediaId, createUpdateTime)
      replaceGenreMedia(fileTagInfo, mediaId, createUpdateTime)
      replaceComposerMedia(fileTagInfo, mediaId, createUpdateTime)
    } else {
      logIgnoring(audioInfo, duration, minimumDuration)
    }
  }

  private fun Transaction.replaceComposerMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis
  ) {
    composerMediaDao.replaceMediaComposer(
      this,
      composerDao.getOrCreateComposerId(
        this,
        fileTagInfo.composer,
        fileTagInfo.composerSort,
        createUpdateTime
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.replaceGenreMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis
  ) {
    genreMediaDao.replaceMediaGenres(
      this,
      genreDao.getOrCreateGenreIds(
        this,
        fileTagInfo.genres,
        createUpdateTime
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.upsertAlbumArtist(
    fileTagInfo: MediaFileTagInfo,
    createUpdateTime: Millis
  ) = artistDao.upsertArtist(
    this,
    fileTagInfo.albumArtist,
    fileTagInfo.albumArtistSort,
    fileTagInfo.releaseArtistMbid,
    createUpdateTime
  )

  private fun Transaction.upsertAlbum(
    fileTagInfo: MediaFileTagInfo,
    albumArt: Uri,
    albumArtistId: ArtistId,
    createUpdateTime: Millis
  ) = albumDao.upsertAlbum(
    this,
    fileTagInfo.album,
    fileTagInfo.albumSort,
    albumArt,
    albumArtistId,
    fileTagInfo.releaseMbid,
    fileTagInfo.releaseGroupMbid,
    createUpdateTime
  )

  private fun Transaction.upsertMediaArtists(
    fileTagInfo: MediaFileTagInfo,
    createUpdateTime: Millis
  ): ArtistIdList {
    val mediaArtists = fileTagInfo.artists
    val mediaArtistsSort = fileTagInfo.artistsSort
    return ArtistIdList().also { idList ->
      mediaArtists.forEachIndexed { index, artist ->
        val insertMbid = index == 0 && mediaArtists.size == 1 && artist != SongTag.UNKNOWN
        idList += artistDao.upsertArtist(
          this@upsertMediaArtists,
          artist,
          mediaArtistsSort.getOrElse(index) { artist.toArtistSort() },
          if (insertMbid) fileTagInfo.artistMbid else null,
          createUpdateTime
        )
      }
    }
  }

  private fun emitResults(upsertResults: UpsertResults) = with(upsertResults) {
    if (createdMedia.isNotEmpty()) emit(AudioDaoEvent.MediaCreated(createdMedia))
    if (updatedMedia.isNotEmpty()) emit(AudioDaoEvent.MediaUpdated(updatedMedia))
  }

  /**
   * Deleting media also cascades into various relationship tables, as does deleting albums,
   * artists, genres, etc.
   */
  override suspend fun deleteAll() {
    db.transaction {
      eqPresetAssocDao.deleteMediaAndAlbumAssociations(this)
//      playListSongFileTable.deleteAll(txn)
//      queueTable.deleteAll(txn)
      MediaTable.deleteAll()
      albumDao.deleteAll(this)
      artistDao.deleteAll(this)
      composerDao.deleteAll(this)
      genreDao.deleteAll(this)
      artistMediaDao.deleteAll(this)
      genreMediaDao.deleteAll(this)
      composerMediaDao.deleteAll(this)
      artistAlbumDao.deleteAll(this)
    }
  }

  override suspend fun deleteEntitiesWithNoMedia() = db.transaction {
    albumDao.deleteAlbumsWithNoMedia(this).let { albumsDeleted ->
      LOG._i { it("Deleted %d albums with no media", albumsDeleted) }
    }
    artistDao.deleteArtistsWithNoMedia(this).let { artistsDeleted ->
      LOG._i { it("Deleted %d artists with no media", artistsDeleted) }
    }
    genreDao.deleteGenresNotAssociateWithMedia(this).let { genresDeleted ->
      LOG._i { it("Deleted %d genres with no associated media", genresDeleted) }
    }
    composerDao.deleteComposersWithNoMedia(this).let { composersDeleted: Long ->
      LOG._i { it("Deleted %d composers with no media", composersDeleted) }
    }
    /*
    playListTable.updateTotals(txn)
     */
  }

  override suspend fun getCountAllAudio(): LongResult = db.query {
    runCatching { MediaTable.selectCount { mediaType eq MediaType.Audio.id }.count() }
      .mapError { DaoExceptionMessage(it) }
  }

  override suspend fun getAllAudio(limit: Long): AudioDescriptionResult = db.query {
    runCatching { doGetAllAudio(limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAllAudio(limit: Long): List<AudioDescription> = MediaTable
    .join(AlbumTable, INNER, albumId, AlbumTable.id)
    .selects { listOf(id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.mediaType eq MediaType.Audio.id }
    .orderByAsc { title }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    artistId: ArtistId,
    limit: Long
  ): AudioDescriptionResult = db.query {
    runCatching { doGetArtistMedia(artistId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetArtistMedia(
    artistId: ArtistId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(ArtistMediaTable, INNER, id, ArtistMediaTable.mediaId)
    .join(AlbumTable, INNER, albumId, AlbumTable.id)
    .selects { listOf(id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.artistId eq artistId.value }
    .ordersBy {
      listOf(OrderBy(AlbumTable.albumTitle), OrderBy(title))
    }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    albumId: AlbumId,
    limit: Long
  ): AudioDescriptionResult = db.query {
    runCatching { doGetAlbumMedia(albumId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAlbumMedia(
    albumId: AlbumId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
    .selects { listOf(id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.albumId eq albumId.value }
    .orderByAsc { trackNumber }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    genreId: GenreId,
    limit: Long
  ): AudioDescriptionResult = db.query {
    runCatching { doGetGenreMedia(genreId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetGenreMedia(
    genreId: GenreId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(GenreMediaTable, INNER, id, GenreMediaTable.mediaId)
    .join(AlbumTable, INNER, albumId, AlbumTable.id)
    .selects { listOf(id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { GenreMediaTable.genreId eq genreId.value }
    .ordersBy { listOf(OrderBy(AlbumTable.albumTitle), OrderBy(trackNumber)) }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    composerId: ComposerId,
    limit: Long
  ): AudioDescriptionResult {
    TODO("Not yet implemented")
  }

  override suspend fun getAudioQueueItems(shuffled: Boolean): AudioItemListResult = db.query {
    runCatching { doGetAudioQueueItems(shuffled) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAudioQueueItems(shuffled: Boolean): List<AudioQueueItemData> =
    MediaTable
      .join(QueueTable, INNER, id, QueueTable.itemId)
      .join(AlbumTable, INNER, albumId, AlbumTable.id)
      .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
      .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
      .selects {
        listOf(
          MediaTable.id,
          MediaTable.location,
          MediaTable.title,
          AlbumTable.albumTitle,
          AlbumTable.id,
          MediaTable.rating,
          MediaTable.duration,
          MediaTable.trackNumber,
          AlbumArtistTable[ArtistTable.artistName],
          SongArtistTable[ArtistTable.artistName],
          AlbumTable.albumLocalArtUri,
          AlbumTable.albumArtUri
        )
      }
      .where { QueueTable.queueId eq QUEUE_ID.value and (QueueTable.shuffled eq shuffled) }
      .sequence { cursor ->
        val id = cursor[MediaTable.id]
        val albumTitle = cursor[AlbumTable.albumTitle]
        AudioQueueItemData(
          MediaId(id),
          cursor[MediaTable.location].toUriOrEmpty(),
          cursor[MediaTable.title].toTitle(),
          albumTitle.toAlbumTitle(),
          AlbumId(cursor[AlbumTable.id]),
          cursor[AlbumArtistTable[ArtistTable.artistName]].toArtistName(),
          setOf(cursor[SongArtistTable[ArtistTable.artistName]].toArtistName()),
          cursor[MediaTable.rating].toRating(),
          Millis(cursor[MediaTable.duration]),
          cursor[MediaTable.trackNumber],
          cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
          cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
        )
      }
      .toList()
  /**
   * We want to get all the shuffled IDs in correct order, then create a new list using the same
   * objects from the [upNextQueue]. Because [upNextQueue] may contain duplicates we need a
   * data structure to support that. We'll use a map with ID as key and entry will be a list.
   * Removing from the end of the list won't require any copying
   */
  override suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T> {
    if (upNextQueue.isEmpty()) return mutableListOf()
    val queueMap = Long2ObjectOpenHashMap<MutableList<T>>(upNextQueue.size).apply {
      upNextQueue.forEach { item ->
        getOrPut(item.id.value, ::mutableListOf).add(item)
      }
    }
    return ArrayList<T>(upNextQueue.size).apply {
      db.query {
        QueueTable
          .select { itemId }
          .where { queueId eq QUEUE_ID.value and (shuffled eq true) }
          .orderByAsc { queueOrder }
          .sequence { it[itemId] }
          .forEach { mediaId ->
            val item = queueMap.get(mediaId)?.removeLastOrNull()
            if (item != null) {
              add(item)
            } else {
              // Catastrophic as sizes won't match. Make shuffled match upNext as fallback and quit
              LOG.e { it("Item id=%d in shuffled that does NOT appear in upNextQueue.", mediaId) }
              clear()
              addAll(upNextQueue)
              return@query
            }
          }
      }
    }
  }

  override suspend fun getAudioItemsForQueue(idList: LongList): AudioItemListResult = db.query {
    runCatching { doGetAudioItemsForQueue(idList) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAudioItemsForQueue(idList: LongList): List<AudioQueueItemData> {
    if (idList.isEmpty()) return emptyList()
    return try {
      // An IN query doesn't return results in the order of the in list, so we create a map and
      // iterate the idList and map using the Long2ObjectMap
      val idToItemMap: Long2ObjectMap<AudioQueueItemData> = MediaTable
        .join(AlbumTable, INNER, albumId, AlbumTable.id)
        .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
        .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
        .selects {
          listOf(
            MediaTable.id,
            MediaTable.location,
            MediaTable.title,
            AlbumTable.albumTitle,
            AlbumTable.id,
            MediaTable.rating,
            MediaTable.duration,
            MediaTable.trackNumber,
            AlbumArtistTable[ArtistTable.artistName],
            SongArtistTable[ArtistTable.artistName],
            AlbumTable.albumLocalArtUri,
            AlbumTable.albumArtUri
          )
        }
        .where { MediaTable.id inList idList }
        .sequence { cursor ->
          val id = cursor[MediaTable.id]
          val albumTitle = cursor[AlbumTable.albumTitle]
          AudioQueueItemData(
            MediaId(id),
            cursor[MediaTable.location].toUriOrEmpty(),
            cursor[MediaTable.title].toTitle(),
            albumTitle.toAlbumTitle(),
            AlbumId(cursor[AlbumTable.id]),
            cursor[AlbumArtistTable[ArtistTable.artistName]].toArtistName(),
            setOf(cursor[SongArtistTable[ArtistTable.artistName]].toArtistName()),
            cursor[MediaTable.rating].toRating(),
            Millis(cursor[MediaTable.duration]),
            cursor[MediaTable.trackNumber],
            cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
            cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
          )
        }
        .associateByTo(Long2ObjectOpenHashMap(DEFAULT_MAP_SIZE)) { item -> item.id() }

      idList.mapNotNull { idToItemMap[it] }
    } catch (e: Exception) {
      // Something failed with IN style query (inList too large?), so let's iterate
      LOG.e(e) { it("Getting queue audio items IN select error, try iterating") }
      idList.map { id ->
        SINGLE_QUEUE_ITEM_DATA_QUERY
          .sequence({ it[MediaTable.id] = id }) { cursor ->
            AudioQueueItemData(
              MediaId(cursor[AudioViewQueueData.mediaId]),
              cursor[AudioViewQueueData.mediaLocation].toUriOrEmpty(),
              cursor[AudioViewQueueData.mediaTitle].toTitle(),
              cursor[AudioViewQueueData.albumTitle].toAlbumTitle(),
              AlbumId(cursor[AudioViewQueueData.albumId]),
              cursor[AudioViewQueueData.albumArtistName].toArtistName(),
              setOf(cursor[AudioViewQueueData.songArtistName].toArtistName()),
              cursor[AudioViewQueueData.rating].toRating(),
              Millis(cursor[AudioViewQueueData.duration]),
              cursor[AudioViewQueueData.trackNumber],
              cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
              cursor[AlbumTable.albumArtUri].toUriOrEmpty(),
            )
          }
          .single()
      }
    }
  }

  override suspend fun getNextAlbumList(
    albumTitle: AlbumTitle
  ): Result<AudioIdList, DaoMessage> = albumDao.getNextAlbum(albumTitle)
    .flatMap { nameId ->
      when (val albumAudio = getAllAudioFor(nameId.albumId, Long.MAX_VALUE)) {
        is Ok -> {
          val list: List<AudioDescription> = albumAudio.value
          val idList = MediaIdList(list.mapTo(LongArrayList(list.size)) { it.mediaId.value })
          Ok(AudioIdList(idList, SongListType.Album, nameId.albumTitle.value))
        }
        is Err -> Err(DaoNotFound(albumTitle.value))
      }
    }

  override suspend fun getNextArtistList(
    artistName: ArtistName
  ): Result<AudioIdList, DaoMessage> = artistDao.getNextArtist(artistName)
    .flatMap { idName ->
      when (val artistAudio = getAllAudioFor(idName.artistId, Long.MAX_VALUE)) {
        is Ok -> {
          val list: List<AudioDescription> = artistAudio.value
          val idList = MediaIdList(list.mapTo(LongArrayList(list.size)) { it.mediaId.value })
          Ok(AudioIdList(idList, SongListType.Artist, idName.artistName.value))
        }
        is Err -> Err(DaoNotFound(artistName.value))
      }
    }

  override suspend fun getNextComposerList(
    composerName: ComposerName
  ): Result<AudioIdList, DaoMessage> = composerDao.getNextComposer(composerName)
    .flatMap { idName ->
      when (val composerAudio = getAllAudioFor(idName.composerId, Long.MAX_VALUE)) {
        is Ok -> {
          val list: List<AudioDescription> = composerAudio.value
          val idList = MediaIdList(list.mapTo(LongArrayList(list.size)) { it.mediaId.value })
          Ok(AudioIdList(idList, SongListType.Composer, idName.composerName.value))
        }
        is Err -> Err(DaoNotFound(composerName.value))
      }
    }

  override fun incrementPlayedCount(id: MediaId) {
    scope.launch {
      db.transaction {
        INCREMENT_PLAYED_COUNT.update {
          it[BIND_CURRENT_TIME] = System.currentTimeMillis()
          it[BIND_MEDIA_ID] = id.value
        }
      }
    }
  }

  override fun incrementSkippedCount(id: MediaId) {
    scope.launch {
      db.transaction {
        INCREMENT_SKIPPED_COUNT.update {
          it[BIND_CURRENT_TIME] = System.currentTimeMillis()
          it[BIND_MEDIA_ID] = id.value
        }
      }
    }
  }

  override fun updateDuration(id: MediaId, newDuration: Millis) {
    scope.launch {
      val rowsUpdated = db.transaction {
        MediaTable.updateColumns {
          it[duration] = newDuration()
          it[updatedTime] = System.currentTimeMillis()
        }.where {
          MediaTable.id eq id.value
        }.update()
      }
      if (rowsUpdated == 0L)
        LOG.e { it(CANT_UPDATE_DURATION, newDuration, id) }
    }
  }

  override suspend fun setRating(id: MediaId, newRating: Rating): Rating {
    val rowsUpdated = db.transaction {
      MediaTable.updateColumns {
        it[rating] = newRating()
        it[updatedTime] = System.currentTimeMillis()
      }.where {
        MediaTable.id eq id.value
      }.update()
    }
    return if (rowsUpdated == 1L) newRating
    else {
      val msg = "Failed updating $id rating to $newRating"
      LOG.e { it(msg) }
      throw DaoException(msg)
    }
  }

  override suspend fun getMediaTitle(mediaId: MediaId): Result<Title, DaoMessage> = db.query {
    runCatching { doGetMediaTitle(mediaId) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetMediaTitle(mediaId: MediaId): Title = MediaTable
    .select(title)
    .where { id eq mediaId.value }
    .sequence { Title(it[title]) }
    .single()

  /**
   * While there could be a race condition if 2 threads tried to insert the same media, it's
   * expected this is called by the media scanner which would mean there is no possible race
   * condition between update and insert.
   */
  private fun Transaction.updateOrInsertAudioMedia(
    audioInfo: AudioInfo,
    newAlbumId: AlbumId,
    newAlbumArtistId: ArtistId,
    newArtistId: ArtistId,
    fileTagInfo: MediaFileTagInfo,
    createUpdateTime: Millis,
    upsertResults: UpsertResults
  ): MediaId {
    val formatFromExt = MediaFormat.mediaFormatFromExtension(audioInfo.path.extension)
    return maybeUpdateAudioMedia(
      audioInfo,
      newAlbumId,
      newAlbumArtistId,
      newArtistId,
      fileTagInfo,
      createUpdateTime,
      upsertResults
    ) ?: INSERT_AUDIO_STATEMENT.insert {
      it[location] = audioInfo.location.toString()
      it[contentId] = audioInfo.id.prop
      it[mediaType] = MediaType.Audio.id
      it[mediaFormat] = formatFromExt.id
      it[title] = fileTagInfo.title
      it[titleSort] = fileTagInfo.titleSort
      it[albumId] = newAlbumId.value
      it[albumArtistId] = newAlbumArtistId.value
      it[artistId] = newArtistId.value
      it[year] = fileTagInfo.year
      it[rating] = fileTagInfo.rating.toRating().value
      it[duration] = fileTagInfo.duration()
      it[trackNumber] = fileTagInfo.trackNumber
      it[totalTracks] = fileTagInfo.totalTracks
      it[discNumber] = fileTagInfo.discNumber
      it[totalDiscs] = fileTagInfo.totalDiscs
      it[comment] = fileTagInfo.comment
      it[trackMbid] = fileTagInfo.trackMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
    }.toMediaId().also { id -> upsertResults.createdMedia += id }
  }

  private fun Transaction.maybeUpdateAudioMedia(
    newInfo: AudioInfo,
    newAlbumId: AlbumId,
    newAlbumArtistId: ArtistId,
    newArtistId: ArtistId,
    fileTagInfo: MediaFileTagInfo,
    newUpdateTime: Millis,
    upsertResults: UpsertResults
  ): MediaId? = queryAudioForUpdate(newInfo.location)?.let { info ->
    val updateTitle = info.title.updateOrNull { fileTagInfo.title }
    val updateTitleSort = info.titleSort.updateOrNull { fileTagInfo.titleSort }
    val updateAlbumId = info.albumId.updateOrNull { newAlbumId }
    val updateAlbumArtistId = info.albumArtistId.updateOrNull { newAlbumArtistId }
    val updateArtistId = info.artistId.updateOrNull { newArtistId }
    val updateYear = info.year.updateOrNull { fileTagInfo.year }
    val updateRating = info.rating.updateOrNull { fileTagInfo.rating }
    val updateDuration = info.duration.updateOrNull { fileTagInfo.duration }
    val updateTrackNumber = info.trackNumber.updateOrNull { fileTagInfo.trackNumber }
    val updateTotalTracks = info.totalTracks.updateOrNull { fileTagInfo.totalTracks }
    val updateDiscNumber = info.trackNumber.updateOrNull { fileTagInfo.discNumber }
    val updateTotalDiscs = info.totalDiscs.updateOrNull { fileTagInfo.totalDiscs }
    val updateComment = info.comment.updateOrNull { fileTagInfo.comment }
    val updateTrackMbid = info.trackMbid?.updateOrNull { fileTagInfo.trackMbid }

    val updateNeeded = anyNotNull {
      arrayOf(
        updateTitle,
        updateTitleSort,
        updateAlbumId,
        updateAlbumArtistId,
        updateArtistId,
        updateYear,
        updateRating,
        updateDuration,
        updateTrackNumber,
        updateTotalTracks,
        updateDiscNumber,
        updateTotalDiscs,
        updateComment,
        updateTrackMbid
      )
    }

    if (updateNeeded) {
      val updated = MediaTable.updateColumns {
        updateTitle?.let { update -> it[title] = update }
        updateTitleSort?.let { update -> it[titleSort] = update }
        updateAlbumId?.let { update -> it[albumId] = update.value }
        updateAlbumArtistId?.let { update -> it[albumArtistId] = update.value }
        updateArtistId?.let { update -> it[artistId] = update.value }
        updateYear?.let { update -> it[year] = update }
        updateRating?.let { update -> it[rating] = update.toRating().value }
        updateDuration?.let { update -> it[duration] = update() }
        updateTrackNumber?.let { update -> it[trackNumber] = update }
        updateTotalTracks?.let { update -> it[totalTracks] = update }
        updateDiscNumber?.let { update -> it[discNumber] = update }
        updateTotalDiscs?.let { update -> it[totalDiscs] = update }
        updateComment?.let { update -> it[comment] = update }
        updateTrackMbid?.let { update -> it[trackMbid] = update.value }
        it[updatedTime] = newUpdateTime()
      }.where { id eq info.id.value }.update()

      if (updated >= 1) upsertResults.updatedMedia += info.id
      else LOG.e { it("Could not update ${info.title}") }
    }
    info.id
  }

  private fun Queryable.queryAudioForUpdate(location: Uri): AudioForUpdate? = QUERY_AUDIO_FOR_UPDATE
    .sequence({ it[0] = location.toString() }) {
      AudioForUpdate(
        it[id].toMediaId(),
        it[title],
        it[titleSort],
        it[albumId].toAlbumId(),
        it[albumArtistId].toArtistId(),
        it[artistId].toArtistId(),
        it[year],
        it[rating].toRating().toStarRating(),
        Millis(it[duration]),
        it[trackNumber],
        it[totalTracks],
        it[discNumber],
        it[totalDiscs],
        it[comment],
        it[trackMbid].toTrackMbidOrNull()
      )
    }.singleOrNull()

  private fun shouldNotIgnore(duration: Millis, prefs: AppPrefs) =
    !prefs.ignoreSmallFiles() || duration > prefs.ignoreThreshold()

  private fun logIgnoring(audioInfo: AudioInfo, duration: Millis, minimumDuration: Millis) {
    LOG.i {
      it(
        "Ignoring %s duration:%d < threshold:%d",
        audioInfo.title,
        duration(),
        minimumDuration()
      )
    }
  }
}

private val INSERT_AUDIO_STATEMENT = MediaTable.insertValues {
  it[location].bindArg()
  it[contentId].bindArg()
  it[mediaType].bindArg()
  it[mediaFormat].bindArg()
  it[title].bindArg()
  it[titleSort].bindArg()
  it[albumId].bindArg()
  it[albumArtistId].bindArg()
  it[artistId].bindArg()
  it[year].bindArg()
  it[rating].bindArg()
  it[duration].bindArg()
  it[trackNumber].bindArg()
  it[totalTracks].bindArg()
  it[discNumber].bindArg()
  it[totalDiscs].bindArg()
  it[comment].bindArg()
  it[trackMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private class AudioForUpdate(
  val id: MediaId,
  val title: String,
  val titleSort: String,
  val albumId: AlbumId,
  val albumArtistId: ArtistId,
  val artistId: ArtistId,
  val year: Int,
  val rating: StarRating,
  val duration: Millis,
  val trackNumber: Int,
  val totalTracks: Int,
  val discNumber: Int,
  val totalDiscs: Int,
  val comment: String,
  val trackMbid: TrackMbid?
)

private fun AudioInfo.uriToParse(): Uri {
  return if (path.exists()) path.toUri() else location
}

fun String.toTrackMbidOrNull(): TrackMbid? {
  return if (isValidMbid()) TrackMbid(this) else null
}

class UpsertResults(initialCapacity: Int) {
  val createdMedia = MediaIdList(initialCapacity)
  val updatedMedia = MediaIdList(initialCapacity)
}

private val AlbumArtistTable = ArtistTable.alias("AlbumArtist")
private val SongArtistTable = ArtistTable.alias("SongArtist")

// private val QueueAudioViewQuery: QueryBuilder<Join> = MediaTable
//  .join(QueueTable, INNER, id, QueueTable.itemId) {
//    (QueueTable.queueId eq LOCAL_AUDIO_QUEUE_ID) and (QueueTable.shuffled eq false)
//  }
//  .join(AlbumTable, INNER, albumId, AlbumTable.id)
//  .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
//  .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
//  .selects {
//    listOf(
//      MediaTable.id,
//      location,
//      title,
//      AlbumTable.albumTitle,
//      AlbumTable.id,
//      MediaTable.rating,
//      MediaTable.duration,
//      trackNumber,
//      AlbumArtistTable[ArtistTable.artistName],
//      SongArtistTable[ArtistTable.artistName]
//    )
//  }
//  .where { MediaTable.mediaType eq MediaType.Audio.id }
//  .orderByAsc { QueueTable.queueOrder }

private val AudioViewQueueDataQuery: QueryBuilder<Join> = MediaTable
  .join(AlbumTable, INNER, albumId, AlbumTable.id)
  .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
  .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
  .selects {
    listOf(
      MediaTable.id,
      MediaTable.location,
      MediaTable.title,
      AlbumTable.albumTitle,
      AlbumTable.id,
      MediaTable.rating,
      MediaTable.duration,
      MediaTable.trackNumber,
      AlbumArtistTable[ArtistTable.artistName],
      SongArtistTable[ArtistTable.artistName],
      AlbumTable.albumLocalArtUri,
      AlbumTable.albumArtUri
    )
  }
  .where { MediaTable.mediaType eq MediaType.Audio.id }

object AudioViewQueueData : View(AudioViewQueueDataQuery) {
  val mediaId = column(MediaTable.id)
  val mediaLocation = column(MediaTable.location)
  val mediaTitle = column(MediaTable.title)
  val albumTitle = column(AlbumTable.albumTitle)
  val albumId = column(AlbumTable.id)
  val rating = column(MediaTable.rating)
  val duration = column(MediaTable.duration)
  val trackNumber = column(MediaTable.trackNumber)
  val albumArtistName = column(AlbumArtistTable[ArtistTable.artistName], "AlbumArtist")
  val songArtistName = column(SongArtistTable[ArtistTable.artistName], "SongArtist")
  val localAlbumArt = column(AlbumTable.albumLocalArtUri)
  val albumArt = column(AlbumTable.albumArtUri)
}

private val FullAudioViewQuery = MediaTable
  .join(AlbumTable, INNER, albumId, AlbumTable.id)
  .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
  .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
  .join(GenreMediaTable, INNER, id, GenreMediaTable.mediaId)
  .join(GenreTable, LEFT, GenreTable.id, GenreMediaTable.genreId)
  .join(ComposerMediaTable, INNER, id, ComposerMediaTable.mediaId)
  .join(ComposerTable, LEFT, ComposerTable.id, ComposerMediaTable.composerId)
  .selects {
    listOf(
      id,
      title,
      AlbumTable.albumTitle,
      AlbumArtistTable[ArtistTable.artistName],
      SongArtistTable[ArtistTable.artistName],
      GenreTable.genre,
      ComposerTable.composer
    )
  }
  .where { MediaTable.mediaType eq MediaType.Audio.id }

object FullAudioView : View(FullAudioViewQuery) {
  val mediaId = column(id)
  val mediaTitle = column(title)
  val albumTitle = column(AlbumTable.albumTitle)
  val albumArtistName = column(AlbumArtistTable[ArtistTable.artistName])
  val songArtistName = column(SongArtistTable[ArtistTable.artistName])
  val genreName = column(GenreTable.genre)
  val composeName = column(ComposerTable.composer)
}

// Must bind the item id during the query
private val SINGLE_QUEUE_ITEM_DATA_QUERY by lazy {
  Query(
    MediaTable
      .join(AlbumTable, INNER, albumId, AlbumTable.id)
      .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, AlbumArtistTable[ArtistTable.id])
      .join(SongArtistTable, INNER, MediaTable.artistId, SongArtistTable[ArtistTable.id])
      .selects {
        listOf(
          id,
          location,
          title,
          AlbumTable.albumTitle,
          AlbumTable.id,
          MediaTable.rating,
          MediaTable.duration,
          trackNumber,
          AlbumArtistTable[ArtistTable.artistName],
          SongArtistTable[ArtistTable.artistName]
        )
      }
      .where { (MediaTable.mediaType eq MediaType.Audio.id) and (id eq bindLong()) }
  )
}

private val BIND_CURRENT_TIME = bindLong()
private val BIND_MEDIA_ID = bindLong()
private val INCREMENT_SKIPPED_COUNT by lazy {
  MediaTable.updateColumns {
    it[skippedCount] = skippedCount plus 1
    it[lastSkippedTime] = BIND_CURRENT_TIME
  }.where {
    id eq BIND_MEDIA_ID
  }
}

private val INCREMENT_PLAYED_COUNT by lazy {
  MediaTable.updateColumns {
    it[skippedCount] = playedCount plus 1
    it[lastSkippedTime] = BIND_CURRENT_TIME
  }.where {
    id eq BIND_MEDIA_ID
  }
}
