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
import androidx.core.net.toUri
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.ealvabrainz.brainz.data.isValidMbid
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Title
import com.ealva.toque.common.toMillis
import com.ealva.toque.common.toTitle
import com.ealva.toque.db.MediaTable.albumId
import com.ealva.toque.db.MediaTable.title
import com.ealva.toque.db.MediaTable.trackNumber
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
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
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toRating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.tag.ArtistParserFactory
import com.ealva.toque.tag.SongTag
import com.ealva.toque.tag.toArtistSort
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.OrderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.ordersBy
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

/**
 * This is the primary entry point for the media scanner to persist media information, via the
 * upsertAudioList.
 */
interface AudioMediaDao {
  val audioDaoEvents: SharedFlow<AudioDaoEvent>

  /**
   * Insert or update a list of audio media. This function begins a transaction which will
   * dispatcher on another thread (except in tests). This is the method the media scanner calls
   * to create or update audio media information.
   */
  suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    metadataParser: MediaMetadataParser,
    appPrefs: AppPrefs,
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

  suspend fun getAllAudio(limit: Long): Result<List<AudioDescription>, DaoMessage>

  suspend fun getAllAudioFor(
    artistId: ArtistId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage>

  suspend fun getAllAudioFor(
    albumId: AlbumId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage>

  suspend fun getAllAudioFor(
    genreId: GenreId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage>

  suspend fun getAllAudioFor(
    composerId: ComposerId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage>

  companion object {
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
      dispatcher: CoroutineDispatcher? = null
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
        dispatcher ?: Dispatchers.Main
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

// private val QUERY_AUDIO_ID = MediaTable.select(
//   MediaTable.id
// ).where { location eq bindString() }

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

  override val audioDaoEvents = MutableSharedFlow<AudioDaoEvent>()

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
    appPrefs: AppPrefs,
    createUpdateTime: Millis
  ) {
    val artistParser = artistParserFactory.make()
    db.transaction {
      val upsertResults = UpsertResults(audioInfoList.size)
      audioInfoList.forEach { audioInfo ->
        metadataParser.parseMetadata(audioInfo.uriToParse(), artistParser).use { metadata ->
          upsertAudio(audioInfo, metadata, appPrefs, createUpdateTime, upsertResults)
        }
      }
      onCommit { emitResults(upsertResults) }
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
      LOG.i { it("Deleted %d albums with no media", albumsDeleted) }
    }
    artistDao.deleteArtistsWithNoMedia(this).let { artistsDeleted ->
      LOG.i { it("Deleted %d artists with no media", artistsDeleted) }
    }
    genreDao.deleteGenresNotAssociateWithMedia(this).let { genresDeleted ->
      LOG.i { it("Deleted %d genres with no associated media", genresDeleted) }
    }
    composerDao.deleteComposersWithNoMedia(this).let { composersDeleted: Long ->
      LOG.i { it("Deleted %d composers with no media", composersDeleted) }
    }
    /*
    playListTable.updateTotals(txn)
     */
  }

  override suspend fun getAllAudio(
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage> = db.query {
    runCatching { doGetTrackNames(limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetTrackNames(limit: Long): List<AudioDescription> = MediaTable
    .join(AlbumTable, JoinType.INNER, albumId, AlbumTable.id)
    .selects { listOf(MediaTable.id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.mediaType eq MediaType.Audio.id }
    .orderByAsc { title }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[MediaTable.id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    artistId: ArtistId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage> = db.query {
    runCatching { doGetArtistMedia(artistId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetArtistMedia(
    artistId: ArtistId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(ArtistMediaTable, JoinType.INNER, MediaTable.id, ArtistMediaTable.mediaId)
    .join(AlbumTable, JoinType.INNER, albumId, AlbumTable.id)
    .selects { listOf(MediaTable.id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.artistId eq artistId.value }
    .ordersBy {
      listOf(OrderBy(AlbumTable.albumTitle), OrderBy(title))
    }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[MediaTable.id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    albumId: AlbumId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage> = db.query {
    runCatching { doGetAlbumMedia(albumId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetAlbumMedia(
    albumId: AlbumId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
    .selects { listOf(MediaTable.id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { MediaTable.albumId eq albumId.value }
    .orderByAsc { trackNumber }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[MediaTable.id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    genreId: GenreId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage> = db.query {
    runCatching { doGetGenreMedia(genreId, limit) }
      .mapError { DaoExceptionMessage(it) }
  }

  private fun Queryable.doGetGenreMedia(
    genreId: GenreId,
    limit: Long
  ): List<AudioDescription> = MediaTable
    .join(GenreMediaTable, JoinType.INNER, MediaTable.id, GenreMediaTable.mediaId)
    .join(AlbumTable, JoinType.INNER, albumId, AlbumTable.id)
    .selects { listOf(MediaTable.id, title, AlbumTable.albumLocalArtUri, AlbumTable.albumArtUri) }
    .where { GenreMediaTable.genreId eq genreId.value }
    .ordersBy { listOf(OrderBy(AlbumTable.albumTitle), OrderBy(trackNumber)) }
    .limit(limit)
    .sequence {
      AudioDescription(
        it[MediaTable.id].toMediaId(),
        it[title].toTitle(),
        it[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
        it[AlbumTable.albumArtUri].toUriOrEmpty()
      )
    }
    .toList()

  override suspend fun getAllAudioFor(
    composerId: ComposerId,
    limit: Long
  ): Result<List<AudioDescription>, DaoMessage> {
    TODO("Not yet implemented")
  }

  private fun Transaction.upsertAudio(
    audioInfo: AudioInfo,
    fileTagInfo: MediaFileTagInfo,
    prefs: AppPrefs,
    createUpdateTime: Millis,
    upsertResults: UpsertResults
  ) {
    val duration = fileTagInfo.duration
    if (shouldNotIgnore(duration, prefs)) {
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
      logIgnoring(audioInfo, duration, prefs)
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

//  private fun Transaction.getAudioId(location: Uri): MediaId? = QUERY_AUDIO_ID
//    .sequence({ it[0] = location.toString() }) {
//      it[id].toMediaId()
//    }.singleOrNull()

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
      it[duration] = fileTagInfo.duration.value
      it[trackNumber] = fileTagInfo.trackNumber
      it[totalTracks] = fileTagInfo.totalTracks
      it[discNumber] = fileTagInfo.discNumber
      it[totalDiscs] = fileTagInfo.totalDiscs
      it[comment] = fileTagInfo.comment
      it[trackMbid] = fileTagInfo.trackMbid?.value ?: ""
      it[createdTime] = createUpdateTime.value
      it[updatedTime] = createUpdateTime.value
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
        updateDuration?.let { update -> it[duration] = update.value }
        updateTrackNumber?.let { update -> it[trackNumber] = update }
        updateTotalTracks?.let { update -> it[totalTracks] = update }
        updateDiscNumber?.let { update -> it[discNumber] = update }
        updateTotalDiscs?.let { update -> it[totalDiscs] = update }
        updateComment?.let { update -> it[comment] = update }
        updateTrackMbid?.let { update -> it[trackMbid] = update.value }
        it[updatedTime] = newUpdateTime.value
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
        it[duration].toMillis(),
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

  private fun logIgnoring(audioInfo: AudioInfo, duration: Millis, prefs: AppPrefs) {
    LOG.i {
      it(
        "Ignoring %s duration:%d < threshold:%d",
        audioInfo.title,
        duration,
        prefs.ignoreThreshold()
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
