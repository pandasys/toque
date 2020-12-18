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
import com.ealva.ealvabrainz.brainz.data.toTrackMbid
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.media.MediaFormat
import com.ealva.toque.media.MediaMetadata
import com.ealva.toque.media.MediaMetadataParser
import com.ealva.toque.media.MediaType
import com.ealva.toque.media.StarRating
import com.ealva.toque.media.toRating
import com.ealva.toque.prefs.AppPreferences
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

private val LOG by lazyLogger(AudioMediaDao::class)

interface AudioMediaDao {
  /**
   * Insert or update a list of audio media.
   */
  suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    metadataParser: MediaMetadataParser,
    appPrefs: AppPreferences,
    createUpdateTime: Long
  )

  /**
   * Delete all audio media and related entities, including cascading into relationships
   */
  suspend fun deleteAll()

  /**
   * Delete all audio related entities which no longer have relationships with audio media
   */
  suspend fun deleteEntitiesWithNoMedia()

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
      composerMediaDao: ComposerMediaDao
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
        composerMediaDao
      )
    }
  }
}

private fun AudioInfo.uriToParse(): Uri {
  return if (path.exists()) path.toUri() else location
}

private val QUERY_AUDIO_FOR_UPDATE = MediaTable.select(
  MediaTable.id,
  MediaTable.title,
  MediaTable.titleSort,
  MediaTable.albumId,
  MediaTable.albumArtistId,
  MediaTable.artistId,
  MediaTable.year,
  MediaTable.rating,
  MediaTable.duration,
  MediaTable.trackNumber,
  MediaTable.totalTracks,
  MediaTable.discNumber,
  MediaTable.totalDiscs,
  MediaTable.comment,
  MediaTable.trackMbid
).where { MediaTable.location eq bindString() }

private val QUERY_AUDIO_ID = MediaTable.select(
  MediaTable.id
).where { MediaTable.location eq bindString() }

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
  private val composerMediaDao: ComposerMediaDao
) : AudioMediaDao {
  /**
   * Parse the file tags and create/update all media relationships. Usually the list would be of one
   * album/artist. There is no contract that the list is only one album/artist or that every track
   * on the album is included.
   */
  override suspend fun upsertAudioList(
    audioInfoList: List<AudioInfo>,
    metadataParser: MediaMetadataParser,
    appPrefs: AppPreferences,
    createUpdateTime: Long
  ) {
    val artistParser = artistParserFactory.make()
    db.transaction {
      audioInfoList.forEach { audioInfo ->
        metadataParser.parseMetadata(audioInfo.uriToParse(), artistParser).use { metadata ->
          upsertAudio(audioInfo, metadata, appPrefs, createUpdateTime)
        }
      }
    }
  }

  /**
   * Deleting media also cascades into various relationship tables, as does deleting albums,
   * artists, genres, etc.
   */
  override suspend fun deleteAll() {
    db.transaction {
//      eqPresetAssociationTable.deleteSongAndAlbumAssociations(txn)
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

  private fun Transaction.upsertAudio(
    audioInfo: AudioInfo,
    metadata: MediaMetadata,
    prefs: AppPreferences,
    createUpdateTime: Long
  ) {
    val duration = metadata.duration
    if (shouldNotIgnore(duration, prefs)) {
      val albumArtistId = upsetAlbumArtist(metadata, createUpdateTime)
      val albumId = upsertAlbum(metadata, createUpdateTime)
      artistAlbumDao.insertArtistAlbum(this, albumArtistId, albumId, createUpdateTime)
      val mediaArtistsIds = upsertMediaArtists(metadata, createUpdateTime)
      val mediaId = updateOrInsertAudioMedia(
        audioInfo,
        albumId,
        albumArtistId,
        mediaArtistsIds.first(),
        metadata,
        createUpdateTime
      )
      artistMediaDao.replaceMediaArtists(this, mediaArtistsIds, mediaId, createUpdateTime)
      replaceGenreMedia(metadata, createUpdateTime, mediaId)
      replaceComposerMedia(metadata, createUpdateTime, mediaId)
    } else {
      logIgnoring(audioInfo, duration, prefs)
    }
  }

  private fun Transaction.replaceComposerMedia(
    metadata: MediaMetadata,
    createUpdateTime: Long,
    mediaId: MediaId
  ) {
    composerMediaDao.replaceMediaComposer(
      this,
      composerDao.getOrCreateComposerId(
        this,
        metadata.composer,
        metadata.composerSort,
        createUpdateTime
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.replaceGenreMedia(
    metadata: MediaMetadata,
    createUpdateTime: Long,
    mediaId: MediaId
  ) {
    genreMediaDao.replaceMediaGenres(
      this,
      genreDao.getOrCreateGenreIds(
        this,
        metadata.genres,
        createUpdateTime
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.upsertAlbum(
    metadata: MediaMetadata,
    createUpdateTime: Long
  ) = albumDao.upsertAlbum(
    this,
    metadata.album,
    metadata.albumSort,
    metadata.releaseMbid,
    metadata.releaseGroupMbid,
    createUpdateTime
  )

  private fun Transaction.upsetAlbumArtist(
    metadata: MediaMetadata,
    createUpdateTime: Long
  ) = artistDao.upsertArtist(
    this,
    metadata.albumArtist,
    metadata.albumArtistSort,
    metadata.releaseArtistMbid,
    createUpdateTime
  )

  private fun Transaction.upsertMediaArtists(
    metadata: MediaMetadata,
    createUpdateTime: Long
  ): ArtistIdList {
    val mediaArtists = metadata.artists
    val mediaArtistsSort = metadata.artistsSort
    return ArtistIdList().also { idList ->
      mediaArtists.forEachIndexed { index, artist ->
        val insertMbid = index == 0 && mediaArtists.size == 1 && artist != SongTag.UNKNOWN
        idList += artistDao.upsertArtist(
          this@upsertMediaArtists,
          artist,
          mediaArtistsSort.getOrElse(index) { artist.toArtistSort() },
          if (insertMbid) metadata.artistMbid else null,
          createUpdateTime
        )
      }
    }
  }

  private fun Transaction.getAudioId(location: Uri): MediaId? = QUERY_AUDIO_ID
    .sequence({ it[0] = location.toString() }) {
      it[MediaTable.id].toMediaId()
    }.singleOrNull()

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
    metadata: MediaMetadata,
    createUpdateTime: Long
  ): MediaId {
    val formatFromExt = MediaFormat.mediaFormatFromExtension(audioInfo.path.extension)
    return maybeUpdateAudioMedia(
      audioInfo,
      newAlbumId,
      newAlbumArtistId,
      newArtistId,
      metadata,
      createUpdateTime
    ) ?: INSERT_AUDIO_STATEMENT.insert {
      it[location] = audioInfo.location.toString()
      it[contentId] = audioInfo.id.value
      it[mediaType] = MediaType.Audio.id
      it[mediaFormat] = formatFromExt.id
      it[title] = metadata.title
      it[titleSort] = metadata.titleSort
      it[albumId] = newAlbumId.id
      it[albumArtistId] = newAlbumArtistId.id
      it[artistId] = newArtistId.id
      it[year] = metadata.year
      it[rating] = metadata.rating.toRating().value
      it[duration] = metadata.duration
      it[trackNumber] = metadata.trackNumber
      it[totalTracks] = metadata.totalTracks
      it[discNumber] = metadata.discNumber
      it[totalDiscs] = metadata.totalDiscs
      it[comment] = metadata.comment
      it[trackMbid] = metadata.trackMbid?.value ?: ""
      it[createdTime] = createUpdateTime
      it[updatedTime] = createUpdateTime
    }.toMediaId()
  }

  private fun Transaction.maybeUpdateAudioMedia(
    newInfo: AudioInfo,
    newAlbumId: AlbumId,
    newAlbumArtistId: ArtistId,
    newArtistId: ArtistId,
    metadata: MediaMetadata,
    newUpdateTime: Long
  ): MediaId? = queryAudioForUpdate(newInfo.location)?.let { info ->
    val updateTitle = info.title.updateOrNull { metadata.title }
    val updateTitleSort = info.titleSort.updateOrNull { metadata.titleSort }
    val updateAlbumId = info.albumId.updateOrNull { newAlbumId }
    val updateAlbumArtistId = info.albumArtistId.updateOrNull { newAlbumArtistId }
    val updateArtistId = info.artistId.updateOrNull { newArtistId }
    val updateYear = info.year.updateOrNull { metadata.year }
    val updateRating = info.rating.updateOrNull { metadata.rating }
    val updateDuration = info.duration.updateOrNull { metadata.duration }
    val updateTrackNumber = info.trackNumber.updateOrNull { metadata.trackNumber }
    val updateTotalTracks = info.totalTracks.updateOrNull { metadata.totalTracks }
    val updateDiscNumber = info.trackNumber.updateOrNull { metadata.discNumber }
    val updateTotalDiscs = info.totalDiscs.updateOrNull { metadata.totalDiscs }
    val updateComment = info.comment.updateOrNull { metadata.comment }
    val updateTrackMbid = info.trackMbid?.updateOrNull { metadata.trackMbid }

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
        updateAlbumId?.let { update -> it[albumId] = update.id }
        updateAlbumArtistId?.let { update -> it[albumArtistId] = update.id }
        updateArtistId?.let { update -> it[artistId] = update.id }
        updateYear?.let { update -> it[year] = update }
        updateRating?.let { update -> it[rating] = update.toRating().value }
        updateDuration?.let { update -> it[duration] = update }
        updateTrackNumber?.let { update -> it[trackNumber] = update }
        updateTotalTracks?.let { update -> it[totalTracks] = update }
        updateDiscNumber?.let { update -> it[discNumber] = update }
        updateTotalDiscs?.let { update -> it[totalDiscs] = update }
        updateComment?.let { update -> it[comment] = update }
        updateTrackMbid?.let { update -> it[trackMbid] = update.value }
        it[updatedTime] = newUpdateTime
      }.where { id eq info.id.id }.update()

      if (updated < 1) LOG.e { it("Could not update ${info.title}") }
    }
    info.id
  }

  private fun Queryable.queryAudioForUpdate(location: Uri): AudioForUpdate? = QUERY_AUDIO_FOR_UPDATE
    .sequence({ it[0] = location.toString() }) {
      AudioForUpdate(
        it[MediaTable.id].toMediaId(),
        it[MediaTable.title],
        it[MediaTable.titleSort],
        it[MediaTable.albumId].toAlbumId(),
        it[MediaTable.albumArtistId].toArtistId(),
        it[MediaTable.artistId].toArtistId(),
        it[MediaTable.year],
        it[MediaTable.rating].toRating().toStarRating(),
        it[MediaTable.duration],
        it[MediaTable.trackNumber],
        it[MediaTable.totalTracks],
        it[MediaTable.discNumber],
        it[MediaTable.totalDiscs],
        it[MediaTable.comment],
        it[MediaTable.trackMbid].toTrackMbidOrNull()
      )
    }.singleOrNull() // TODO firstOrNull causes many resource not closed log statements. Why?
  // TODO It appears to be the underlying Cursor not being closed, but it's in a use{} block

  private fun shouldNotIgnore(duration: Long, prefs: AppPreferences) =
    !prefs.ignoreSmallFiles() || duration > prefs.ignoreThreshold()

  private fun logIgnoring(audioInfo: AudioInfo, duration: Long, prefs: AppPreferences) {
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
  val duration: Long,
  val trackNumber: Int,
  val totalTracks: Int,
  val discNumber: Int,
  val totalDiscs: Int,
  val comment: String,
  val trackMbid: TrackMbid?
)

fun String.toTrackMbidOrNull(): TrackMbid? {
  return if (isValidMbid()) toTrackMbid() else null
}
