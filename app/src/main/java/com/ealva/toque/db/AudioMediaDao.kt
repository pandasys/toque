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

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.ealva.ealvabrainz.brainz.data.TrackMbid
import com.ealva.ealvabrainz.brainz.data.isValidMbid
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvabrainz.common.ComposerName
import com.ealva.ealvabrainz.common.GenreName
import com.ealva.ealvabrainz.common.asAlbumTitle
import com.ealva.ealvabrainz.common.asArtistName
import com.ealva.ealvabrainz.common.asComposerName
import com.ealva.ealvabrainz.common.asGenreName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.ShuffleLists
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.Title
import com.ealva.toque.common.asTitle
import com.ealva.toque.common.toRating
import com.ealva.toque.common.toStarRating
import com.ealva.toque.db.AudioMediaDao.Companion.QUEUE_ID
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.AlbumArtistTable
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.SongArtistTable
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.albumArtistTableId
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.albumArtistTableName
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.songArtistTableId
import com.ealva.toque.db.AudioMediaDaoImpl.Companion.songArtistTableName
import com.ealva.toque.db.DaoCommon.ESC_CHAR
import com.ealva.toque.db.PlayListType.File
import com.ealva.toque.db.PlayListType.Rules
import com.ealva.toque.db.PlayListType.System
import com.ealva.toque.db.PlayListType.UserCreated
import com.ealva.toque.file.AudioInfo
import com.ealva.toque.file.extension
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.AlbumIdList
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.ComposerIdList
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.GenreIdList
import com.ealva.toque.persist.HasId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.ealva.toque.persist.asMediaId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.media.MediaFileTagInfo
import com.ealva.toque.service.media.MediaFormat
import com.ealva.toque.service.media.MediaMetadataParser
import com.ealva.toque.service.media.MediaType
import com.ealva.toque.tag.ArtistParserFactory
import com.ealva.toque.tag.SongTag
import com.ealva.toque.tag.toArtistSort
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.expr.isNotNull
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.expr.plus
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType.INNER
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.OrderBy
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.ordersBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.View
import com.ealva.welite.db.view.existingView
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongCollection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val LOG by lazyLogger(AudioMediaDao::class)

sealed interface AudioDaoEvent {
  data class MediaCreated(val id: MediaId) : AudioDaoEvent
  data class MediaUpdated(val id: MediaId) : AudioDaoEvent
}

data class AudioDescription(
  val mediaId: MediaId,
  val title: Title,
  val duration: Millis,
  val rating: Rating,
  val album: AlbumTitle,
  val artist: ArtistName,
  val albumLocalArt: Uri,
  val albumArt: Uri
)

data class AudioQueueItemData(
  val id: MediaId,
  val title: Title,
  val albumTitle: AlbumTitle,
  val albumArtist: ArtistName,
  val location: Uri,
  val fileUri: Uri,
  val displayName: String,
  val albumId: AlbumId,
  val artists: Set<ArtistName>,
  val rating: Rating,
  val duration: Millis,
  val trackNumber: Int,
  val localAlbumArt: Uri,
  val albumArt: Uri
)

@Immutable
data class FullAudioInfo(
  val mediaId: MediaId,
  val title: Title,
  val album: AlbumTitle,
  val albumArtist: ArtistName,
  val songArtist: ArtistName,
  val genre: GenreName,
  val track: Int,
  val totalTracks: Int,
  val disc: Int,
  val totalDiscs: Int,
  val duration: Millis,
  val year: Int,
  val composer: ComposerName,
  val rating: StarRating,
  val playCount: Int,
  val lastPlayed: Millis,
  val skippedCount: Int,
  val lastSkipped: Millis,
  val dateAdded: Millis,
  val location: Uri,
  val file: Uri,
  val albumId: AlbumId,
  val albumArtistId: ArtistId,
)

typealias AudioItemListResult = Result<List<AudioQueueItemData>, Throwable>

/**
 * This is the primary entry point for the media scanner to persist media information, via the
 * upsertAudioList.
 */
interface AudioMediaDao {
  val audioDaoEvents: Flow<AudioDaoEvent>
  val albumDao: AlbumDao
  val artistDao: ArtistDao
  val genreDao: GenreDao
  val composerDao: ComposerDao
  val playlistDao: PlaylistDao

  /**
   * Insert or update a list of audio media. This function begins a transaction which will
   * dispatcher on another thread (except in tests). This is the method the media scanner calls
   * to create or update audio media information.
   */
  suspend fun upsertAudio(
    audioInfo: AudioInfo,
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

  /**
   * Get all audio media using [filter] as an LIKE query and [limit] the returned items.
   *
   * If [filter] is not empty, [MediaTable.title] is like "filter" or [AlbumTable.albumArtist] is
   * like "filter" or [ArtistTable.artistName] is like "filter". Typically the [filter] would be a
   * search string with '%' as a prefix and suffix. "%Led%" would result in matching any of the
   * titles or name containing the characters "led" case insensitive, anywhere in the string; eg.
   * matches artist "Led Zeppelin", song "Sledgehammer", song "Canceled Check", or the album "Led
   * Zeppelin II".
   */
  suspend fun getAllAudio(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getArtistAudio(
    id: ArtistId,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getAlbumArtistAudio(
    id: ArtistId,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  /**
   * Get the songs on the Album with [id]. If [restrictTo] is not null, get only the songs of
   * that artist. When viewing an albums contents, [restrictTo] would be null. When viewing an
   * Album navigated to via an "Artists" screen, [restrictTo] would be that particular Artist.
   */
  suspend fun getAlbumAudio(
    id: AlbumId,
    restrictTo: ArtistId? = null,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getGenreAudio(
    genreId: GenreId,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getComposerAudio(
    composerId: ComposerId,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getPlaylistAudio(
    playlistId: PlaylistId,
    playListType: PlayListType,
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): DaoResult<List<AudioDescription>>

  suspend fun getAudioQueueItems(shuffled: Boolean): AudioItemListResult
  suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T>

  /**
   * Makes a list of AudioQueueItemsData returned in the same order as [idList]
   */
  suspend fun getAudioItemsForQueue(idList: LongCollection): AudioItemListResult

  fun incrementPlayedCount(id: MediaId)
  fun incrementSkippedCount(id: MediaId)
  fun updateDuration(id: MediaId, newDuration: Millis)

  suspend fun setRating(id: MediaId, newRating: Rating): DaoResult<Rating>
  suspend fun getMediaTitle(mediaId: MediaId): DaoResult<Title>

  /** Returns Err if [albumsIds] is empty */
  suspend fun getMediaForAlbums(
    albumsIds: AlbumIdList,
    restrictTo: ArtistId? = null,
    limit: Limit = NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getMediaForArtists(
    artistIds: ArtistIdList,
    limit: Limit = NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getMediaForComposers(
    composerIds: ComposerIdList,
    limit: Limit = NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getMediaForGenres(
    genreIds: GenreIdList,
    limit: Limit = NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    removeDuplicates: Boolean,
    limit: Limit = NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getNextCategory(token: CategoryToken, shuffleLists: ShuffleLists): CategoryMediaList
  suspend fun getPreviousCategory(
    token: CategoryToken,
    shuffleLists: ShuffleLists
  ): CategoryMediaList

  suspend fun getFullInfo(mediaId: MediaId): DaoResult<FullAudioInfo>

  suspend fun getTitleSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  suspend fun getAlbumSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  suspend fun getArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  suspend fun getAlbumArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>>

  companion object {
    private const val LOCAL_AUDIO_QUEUE_ID = 1
    val QUEUE_ID = QueueId(LOCAL_AUDIO_QUEUE_ID)

    fun establishQueueId(txn: TransactionInProgress) = txn.run {
      QueueTable.insert(OnConflict.Ignore) {
        it[queueId] = QUEUE_ID.value
      }
      if ((QueueTable.selectCount { queueId eq QUEUE_ID.value }.longForQuery()) == 0L) {
        throw IllegalStateException("Could not establish queue ID for LocalAudioQueue")
      }
    }

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
      playlistDao: PlaylistDao,
      eqPresetAssociationDao: EqPresetAssociationDao,
      appPrefsSingleton: AppPrefsSingleton,
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
        playlistDao,
        eqPresetAssociationDao,
        appPrefsSingleton,
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
  override val genreDao: GenreDao,
  override val artistDao: ArtistDao,
  override val albumDao: AlbumDao,
  private val artistAlbumDao: ArtistAlbumDao,
  override val composerDao: ComposerDao,
  private val artistMediaDao: ArtistMediaDao,
  private val genreMediaDao: GenreMediaDao,
  private val composerMediaDao: ComposerMediaDao,
  override val playlistDao: PlaylistDao,
  private val eqPresetAssocDao: EqPresetAssociationDao,
  private val appPrefsSingleton: AppPrefsSingleton,
  dispatcher: CoroutineDispatcher
) : AudioMediaDao {
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

  override val audioDaoEvents = MutableSharedFlow<AudioDaoEvent>(extraBufferCapacity = 5)

  private fun emit(event: AudioDaoEvent) {
    scope.launch { audioDaoEvents.emit(event) }
  }

  /**
   * Parse the file tags and create/update all media relationships. There is no contract that the
   * list is only one album/artist or that every track on the album is included.
   */
  override suspend fun upsertAudio(
    audioInfo: AudioInfo,
    metadataParser: MediaMetadataParser,
    minimumDuration: Millis,
    createUpdateTime: Millis
  ) = metadataParser.parseMetadata(audioInfo.uriToParse(), artistParserFactory.make()).use { tag ->
    val appPrefs = appPrefsSingleton.instance()
    db.transaction {
      val upsertResults = AudioUpsertResults { emit(it) }
      onCommit { upsertResults.onCommit() }
      doUpsertAudio(
        audioInfo,
        tag,
        minimumDuration,
        createUpdateTime,
        appPrefs,
        upsertResults
      )
    }
  }

  private fun Transaction.doUpsertAudio(
    audioInfo: AudioInfo,
    fileTagInfo: MediaFileTagInfo,
    minimumDuration: Millis,
    createUpdateTime: Millis,
    appPrefs: AppPrefs,
    upsertResults: AudioUpsertResults
  ) {
    try {
      val duration = fileTagInfo.duration
      if (duration > minimumDuration) {
        val albumArtistId = upsertAlbumArtist(fileTagInfo, createUpdateTime, upsertResults)
        val albumId = upsertAlbum(
          fileTagInfo,
          audioInfo.albumArt,
          createUpdateTime,
          upsertResults
        )
        artistAlbumDao.insertArtistAlbum(this, albumArtistId, albumId, createUpdateTime)
        val mediaArtistsIds = upsertMediaArtists(fileTagInfo, createUpdateTime, upsertResults)
        val mediaId = updateOrInsertAudioMedia(
          audioInfo,
          albumId,
          albumArtistId,
          mediaArtistsIds.first(),
          fileTagInfo,
          createUpdateTime,
          appPrefs,
          upsertResults
        )
        artistMediaDao.replaceMediaArtists(this, mediaArtistsIds, mediaId, createUpdateTime)
        replaceGenreMedia(fileTagInfo, mediaId, createUpdateTime, upsertResults)
        replaceComposerMedia(fileTagInfo, mediaId, createUpdateTime, upsertResults)
      } else {
        logIgnoring(audioInfo, duration, minimumDuration)
      }
    } catch (e: SQLiteConstraintException) {
      LOG.w(e) { it("Constraint violation, probably duplicate media") }
    } catch (e: Exception) {
      LOG.e(e) { it("Unexpected error upserting audio media") }
    }
  }

  private fun Transaction.replaceComposerMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    composerMediaDao.replaceMediaComposer(
      this,
      composerDao.getOrCreateComposerId(
        this,
        fileTagInfo.composer,
        fileTagInfo.composerSort,
        createUpdateTime,
        upsertResults
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.replaceGenreMedia(
    fileTagInfo: MediaFileTagInfo,
    mediaId: MediaId,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) {
    genreMediaDao.replaceMediaGenres(
      this,
      genreDao.getOrCreateGenreIds(
        this,
        fileTagInfo.genres,
        createUpdateTime,
        upsertResults
      ),
      mediaId,
      createUpdateTime
    )
  }

  private fun Transaction.upsertAlbumArtist(
    fileTagInfo: MediaFileTagInfo,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) = artistDao.upsertArtist(
    this,
    fileTagInfo.albumArtist,
    fileTagInfo.albumArtistSort,
    fileTagInfo.releaseArtistMbid,
    createUpdateTime,
    upsertResults
  )

  private fun Transaction.upsertAlbum(
    fileTagInfo: MediaFileTagInfo,
    albumArt: Uri,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ) = albumDao.upsertAlbum(
    this,
    fileTagInfo.album,
    fileTagInfo.albumSort,
    albumArt,
    fileTagInfo.albumArtist,
    fileTagInfo.releaseMbid,
    fileTagInfo.releaseGroupMbid,
    createUpdateTime,
    upsertResults
  )

  private fun Transaction.upsertMediaArtists(
    fileTagInfo: MediaFileTagInfo,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
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
          createUpdateTime,
          upsertResults
        )
      }
    }
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

  override suspend fun getCountAllAudio(): LongResult = runSuspendCatching {
    db.query { MediaTable.selectCount { mediaType eq MediaType.Audio.id }.count() }
  }.mapError { cause -> DaoExceptionMessage(cause) }

//  override suspend fun getAllAudio(
//    filter: Filter,
//    limit: Limit
//  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
//    val view = SmartPlaylist(
//      PlaylistId.INVALID,
//      "First Smart Playlist".asPlaylistName,
//      AnyOrAll.All,
//      NoLimit,
//      SmartOrderBy.Title,
//      EndOfSmartPlaylistAction.Reshuffle,
//      listOf(Rule(1, RuleField.Album, TextMatcher.Contains, MatcherData("Blue", 0, 0))),
//    ).asView()
//
//    val viewId = view.column(MediaTable.id, MediaTable.id.name)
//
//    db.transaction {
//      view.drop()
//      view.create()
//    }
//
//    db.query {
//      view
//        .join(MediaTable, INNER, viewId, MediaTable.id)
//        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
//        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
//        .selects { AUDIO_DESCRIPTION_SELECTS }
//        .where { viewId.isNotNull() }
//        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
//        .toList()
//    }
//  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getAllAudio(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (MediaTable.mediaType eq MediaType.Audio.id).filter(filter) }
        .orderByAsc { MediaTable.titleSort }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getArtistAudio(
    id: ArtistId,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(ArtistMediaTable, INNER, MediaTable.id, ArtistMediaTable.mediaId)
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (MediaTable.artistId eq id.value).filter(filter) }
        .ordersBy { listOf(OrderBy(AlbumTable.albumSort), OrderBy(MediaTable.titleSort)) }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getAlbumArtistAudio(
    id: ArtistId,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(ArtistTable, INNER, MediaTable.albumArtistId, ArtistTable.id)
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (MediaTable.albumArtistId eq id.value).filter(filter) }
        .ordersBy { listOf(OrderBy(AlbumTable.albumSort), OrderBy(MediaTable.trackNumber)) }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getAlbumAudio(
    id: AlbumId,
    restrictTo: ArtistId?,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (MediaTable.albumId eq id.value).restrictTo(restrictTo).filter(filter) }
        .orderByAsc { MediaTable.trackNumber }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getGenreAudio(
    genreId: GenreId,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(GenreMediaTable, INNER, MediaTable.id, GenreMediaTable.mediaId)
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (GenreMediaTable.genreId eq genreId.value).filter(filter) }
        .ordersBy {
          listOf(
            OrderBy(ArtistTable.artistSort),
            OrderBy(MediaTable.year),
            OrderBy(AlbumTable.albumSort),
            OrderBy(MediaTable.discNumber),
            OrderBy(MediaTable.trackNumber)
          )
        }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getComposerAudio(
    composerId: ComposerId,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    db.query {
      MediaTable
        .join(ComposerMediaTable, INNER, MediaTable.id, ComposerMediaTable.mediaId)
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
        .selects { AUDIO_DESCRIPTION_SELECTS }
        .where { (ComposerMediaTable.composerId eq composerId.value).filter(filter) }
        .ordersBy { listOf(OrderBy(AlbumTable.albumTitle), OrderBy(MediaTable.trackNumber)) }
        .limit(limit.value)
        .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getPlaylistAudio(
    playlistId: PlaylistId,
    playListType: PlayListType,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AudioDescription>> = runSuspendCatching {
    when (playListType) {
      UserCreated, System, File -> getPlaylistListedMedia(playlistId, filter, limit)
      Rules -> getSmartPlaylistMedia(playlistId, filter, limit)
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  private suspend fun getPlaylistListedMedia(
    playlistId: PlaylistId,
    filter: Filter,
    limit: Limit
  ): List<AudioDescription> = db.query {
    MediaTable
      .join(PlayListMediaTable, INNER, MediaTable.id, PlayListMediaTable.mediaId)
      .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
      .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
      .selects { AUDIO_DESCRIPTION_SELECTS }
      .where { (PlayListMediaTable.playListId eq playlistId.value).filter(filter) }
      .orderByAsc { PlayListMediaTable.sort }
      .limit(limit.value)
      .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
      .toList()
  }

  private suspend fun getSmartPlaylistMedia(
    playlistId: PlaylistId,
    filter: Filter,
    limit: Limit
  ): List<AudioDescription> = db.query {
    val view = existingView(with(playlistDao) { getPlaylistName(playlistId, Rules) }.value)
    val viewId = view.column(MediaTable.id, MediaTable.id.name)

    view
      .join(MediaTable, INNER, viewId, MediaTable.id)
      .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
      .join(ArtistTable, INNER, MediaTable.artistId, ArtistTable.id)
      .selects { AUDIO_DESCRIPTION_SELECTS }
      .where { viewId.isNotNull() }
      .limit(limit.value)
      .sequence({ bindings -> bindings.filter(filter) }) { cursor -> AudioDescription(cursor) }
      .toList()
  }

  override suspend fun getAudioQueueItems(shuffled: Boolean) = runSuspendCatching {
    db.query { doGetAudioQueueItems(shuffled) }
  }

  private fun Queryable.doGetAudioQueueItems(shuffled: Boolean) = MediaTable
    .join(QueueItemsTable, INNER, MediaTable.id, QueueItemsTable.itemId)
    .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
    .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
    .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
    .selects {
      listOf(
        MediaTable.id,
        MediaTable.location,
        MediaTable.fileUri,
        MediaTable.displayName,
        MediaTable.title,
        AlbumTable.albumTitle,
        AlbumTable.id,
        MediaTable.rating,
        MediaTable.duration,
        MediaTable.trackNumber,
        albumArtistTableName,
        songArtistTableName,
        AlbumTable.albumLocalArtUri,
        AlbumTable.albumArtUri
      )
    }
    .where {
      QueueItemsTable.itemQueueId eq QUEUE_ID.value and (QueueItemsTable.shuffled eq shuffled)
    }
    .sequence { cursor ->
      val id = cursor[MediaTable.id]
      val albumTitle = cursor[AlbumTable.albumTitle]
      AudioQueueItemData(
        MediaId(id),
        cursor[MediaTable.title].asTitle,
        albumTitle.asAlbumTitle,
        cursor[albumArtistTableName].asArtistName,
        cursor[MediaTable.location].toUriOrEmpty(),
        cursor[MediaTable.fileUri].toUriOrEmpty(),
        cursor[MediaTable.displayName],
        AlbumId(cursor[AlbumTable.id]),
        setOf(cursor[songArtistTableName].asArtistName),
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
        QueueItemsTable
          .select { itemId }
          .where {
            itemQueueId eq QUEUE_ID.value and (shuffled eq true)
          }
          .orderByAsc { queueOrder }
          .sequence { cursor -> cursor[itemId] }
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

  override suspend fun getAudioItemsForQueue(idList: LongCollection) = runSuspendCatching {
    db.query { doGetAudioItemsForQueue(idList) }
  }

  private fun Queryable.doGetAudioItemsForQueue(idList: LongCollection): List<AudioQueueItemData> {
    if (idList.isEmpty()) return emptyList()
    return try {
      // An IN query doesn't return results in the order of the in list, so we create a map and
      // iterate the idList and map using the Long2ObjectMap
      val idToItemMap: Long2ObjectMap<AudioQueueItemData> = MediaTable
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
        .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
        .selects {
          listOf(
            MediaTable.id,
            MediaTable.location,
            MediaTable.fileUri,
            MediaTable.displayName,
            MediaTable.title,
            AlbumTable.albumTitle,
            AlbumTable.id,
            MediaTable.rating,
            MediaTable.duration,
            MediaTable.trackNumber,
            albumArtistTableName,
            songArtistTableName,
            AlbumTable.albumLocalArtUri,
            AlbumTable.albumArtUri
          )
        }
        .where { MediaTable.id inList idList }
        .sequence { cursor ->
          val id = cursor[MediaTable.id]
          val anArtist = cursor[songArtistTableName]
          AudioQueueItemData(
            MediaId(id),
            cursor[MediaTable.title].asTitle,
            cursor[AlbumTable.albumTitle].asAlbumTitle,
            cursor[albumArtistTableName].asArtistName,
            cursor[MediaTable.location].toUriOrEmpty(),
            cursor[MediaTable.fileUri].toUriOrEmpty(),
            cursor[MediaTable.displayName],
            AlbumId(cursor[AlbumTable.id]),
            setOf(anArtist.asArtistName),
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
          .sequence({ bindings -> bindings[MediaTable.id] = id }) { cursor ->
            AudioQueueItemData(
              MediaId(cursor[AudioViewQueueData.mediaId]),
              cursor[AudioViewQueueData.mediaTitle].asTitle,
              cursor[AudioViewQueueData.albumTitle].asAlbumTitle,
              cursor[AudioViewQueueData.albumArtistName].asArtistName,
              cursor[AudioViewQueueData.mediaLocation].toUriOrEmpty(),
              cursor[AudioViewQueueData.mediaFileUri].toUriOrEmpty(),
              cursor[AudioViewQueueData.mediaDisplayName],
              AlbumId(cursor[AudioViewQueueData.albumId]),
              setOf(cursor[AudioViewQueueData.songArtistName].asArtistName),
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

  override fun incrementPlayedCount(id: MediaId) {
    scope.launch {
      db.transaction {
        INCREMENT_PLAYED_COUNT.update {
          it[BIND_CURRENT_TIME] = Millis.currentUtcEpochMillis().value
          it[BIND_MEDIA_ID] = id.value
        }
      }
    }
  }

  override fun incrementSkippedCount(id: MediaId) {
    scope.launch {
      db.transaction {
        INCREMENT_SKIPPED_COUNT.update {
          it[BIND_CURRENT_TIME] = Millis.currentUtcEpochMillis().value
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
          it[updatedTime] = Millis.currentUtcEpochMillis().value
        }.where {
          MediaTable.id eq id.value
        }.update()
      }
      if (rowsUpdated == 0L)
        LOG.e { it(CANT_UPDATE_DURATION, newDuration, id) }
    }
  }

  override suspend fun setRating(id: MediaId, newRating: Rating) = runSuspendCatching {
    db.transaction { doSetRating(id, newRating) }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  private fun Transaction.doSetRating(id: MediaId, newRating: Rating): Rating = MediaTable
    .updateColumns {
      it[rating] = newRating()
      it[updatedTime] = Millis.currentUtcEpochMillis().value
    }
    .where { MediaTable.id eq id.value }
    .update()
    .let { rowsUpdated ->
      if (rowsUpdated == 0L) {
        val msg = "Failed updating $id rating to $newRating"
        LOG.e { it(msg) }
        throw DaoException(msg)
      }
      newRating
    }

  override suspend fun getMediaTitle(mediaId: MediaId) = runSuspendCatching {
    db.query {
      MediaTable
        .select(MediaTable.title)
        .where { id eq mediaId.value }
        .sequence { cursor -> Title(cursor[title]) }
        .single()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getMediaForAlbums(
    albumsIds: AlbumIdList,
    restrictTo: ArtistId?,
    limit: Limit,
  ): DaoResult<MediaIdList> = runSuspendCatching {
    require(albumsIds.isNotEmpty)
    db.query {
      MediaTable
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .select(MediaTable.id)
        .where { (MediaTable.albumId inList albumsIds.value).restrictTo(restrictTo) }
        .ordersBy { listOf(OrderBy(AlbumTable.albumSort), OrderBy(MediaTable.trackNumber)) }
        .limit(limit.value)
        .sequence { cursor -> cursor[MediaTable.id] }
        .mapTo(LongArrayList(512)) { it.asMediaId.value }
        .asMediaIdList
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getMediaForArtists(
    artistIds: ArtistIdList,
    limit: Limit
  ): DaoResult<MediaIdList> = runSuspendCatching {
    db.query {
      MediaTable
        .join(ArtistTable, INNER, MediaTable.albumArtistId, ArtistTable.id)
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .select(MediaTable.id)
        .where { (MediaTable.albumArtistId inList artistIds.value) }
        .distinct()
        .ordersBy {
          listOf(
            OrderBy(ArtistTable.artistSort),
            OrderBy(AlbumTable.albumSort),
            OrderBy(MediaTable.trackNumber)
          )
        }
        .limit(limit.value)
        .sequence { cursor -> cursor[MediaTable.id] }
        .mapTo(LongArrayList(512)) { it.asMediaId.value }
        .asMediaIdList
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getMediaForComposers(
    composerIds: ComposerIdList,
    limit: Limit
  ) = runSuspendCatching {
    db.query {
      ComposerMediaTable
        .join(MediaTable, INNER, ComposerMediaTable.mediaId, MediaTable.id)
        .join(ComposerTable, INNER, ComposerMediaTable.composerId, ComposerTable.id)
        .select(MediaTable.id)
        .where { ComposerMediaTable.composerId inList composerIds.value }
        .ordersBy {
          listOf(
            OrderBy(ComposerTable.composerSort),
            OrderBy(MediaTable.titleSort),
          )
        }
        .limit(limit.value)
        .sequence { cursor -> cursor[MediaTable.id] }
        .mapTo(LongArrayList(512)) { it.asMediaId.value }
        .asMediaIdList
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getMediaForGenres(
    genreIds: GenreIdList,
    limit: Limit
  ) = runSuspendCatching {
    db.query {
      GenreMediaTable
        .join(MediaTable, INNER, GenreMediaTable.mediaId, MediaTable.id)
        .join(GenreTable, INNER, GenreMediaTable.genreId, GenreTable.id)
        .select(MediaTable.id)
        .where { GenreMediaTable.genreId inList genreIds.value }
        .distinct()
        .ordersBy {
          listOf(
            OrderBy(GenreTable.genre),
            OrderBy(MediaTable.titleSort),
          )
        }
        .limit(limit.value)
        .sequence { cursor -> cursor[MediaTable.id] }
        .mapTo(LongArrayList(512)) { it.asMediaId.value }
        .asMediaIdList
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    removeDuplicates: Boolean,
    limit: Limit
  ): DaoResult<MediaIdList> = runSuspendCatching {
    db.query {
      PlayListMediaTable
        .join(PlayListTable, INNER, PlayListMediaTable.playListId, PlayListTable.id)
        .select(PlayListMediaTable.mediaId)
        .where { PlayListMediaTable.playListId inList playlistIds.value }
        .distinct()
        .ordersBy {
          listOf(
            PlayListTable.playListName by Order.ASC,
            PlayListMediaTable.sort by Order.ASC
          )
        }
        .limit(limit.value)
        .sequence { cursor -> cursor[PlayListMediaTable.mediaId] }
        .mapTo(LongArrayList(512)) { it.asMediaId.value }
        .asMediaIdList
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getNextCategory(
    token: CategoryToken,
    shuffleLists: ShuffleLists
  ): CategoryMediaList {
    var nextList = CategoryMediaList.EMPTY_ALL_LIST
    val countResult = getCountAllAudio()
    if (countResult is Ok && countResult.value > 0) {
      var nextToken = if (shuffleLists.value) token.getRandom(this) else token.nextToken(this)
      if (nextToken !== CategoryToken.All) {
        var possibleNext = CategoryMediaList(nextToken.getAllMedia(this), nextToken)
        while (possibleNext.isEmpty && nextToken !== CategoryToken.All) {
          nextToken = nextToken.nextToken(this)
          possibleNext = CategoryMediaList(nextToken.getAllMedia(this), nextToken)
        }
        if (nextToken !== CategoryToken.All && possibleNext.isNotEmpty) {
          nextList = possibleNext
        }
      }
    }
    return nextList
  }

  override suspend fun getPreviousCategory(
    token: CategoryToken,
    shuffleLists: ShuffleLists
  ): CategoryMediaList {
    var prevList = CategoryMediaList.EMPTY_ALL_LIST
    val countResult = getCountAllAudio()
    if (countResult is Ok && countResult.value > 0) {
      var prevToken = if (shuffleLists.value) token.getRandom(this) else token.previousToken(this)
      if (prevToken !== CategoryToken.All) {
        var possiblePrev = CategoryMediaList(prevToken.getAllMedia(this), prevToken)
        while (possiblePrev.isEmpty && prevToken !== CategoryToken.All) {
          prevToken = prevToken.previousToken(this)
          possiblePrev = CategoryMediaList(prevToken.getAllMedia(this), prevToken)
        }
        if (prevToken !== CategoryToken.All && possiblePrev.isNotEmpty) prevList = possiblePrev
      }
    }
    return prevList
  }

  override suspend fun getFullInfo(
    mediaId: MediaId
  ): DaoResult<FullAudioInfo> = runSuspendCatching {
    db.query {
      MediaTable
        .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
        .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
        .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
        .join(ComposerMediaTable, INNER, MediaTable.id, ComposerMediaTable.mediaId)
        .join(ComposerTable, INNER, ComposerMediaTable.composerId, ComposerTable.id)
        .selects {
          listOf(
            MediaTable.id,
            MediaTable.title,
            AlbumTable.albumTitle,
            albumArtistTableName,
            songArtistTableName,
            ComposerTable.composer,
            MediaTable.genre,
            MediaTable.trackNumber,
            MediaTable.totalTracks,
            MediaTable.discNumber,
            MediaTable.totalDiscs,
            MediaTable.duration,
            MediaTable.year,
            MediaTable.rating,
            MediaTable.playedCount,
            MediaTable.lastPlayedTime,
            MediaTable.skippedCount,
            MediaTable.lastSkippedTime,
            MediaTable.createdTime,
            MediaTable.location,
            MediaTable.fileUri,
            MediaTable.albumId,
            MediaTable.albumArtistId
          )
        }
        .where { MediaTable.id eq mediaId.value }
        .sequence { cursor ->
          FullAudioInfo(
            MediaId(cursor[MediaTable.id]),
            cursor[MediaTable.title].asTitle,
            cursor[AlbumTable.albumTitle].asAlbumTitle,
            cursor[albumArtistTableName].asArtistName,
            cursor[songArtistTableName].asArtistName,
            cursor[MediaTable.genre].asGenreName,
            cursor[MediaTable.trackNumber],
            cursor[MediaTable.totalTracks],
            cursor[MediaTable.discNumber],
            cursor[MediaTable.totalDiscs],
            Millis(cursor[MediaTable.duration]),
            cursor[MediaTable.year],
            cursor[ComposerTable.composer].asComposerName,
            cursor[MediaTable.rating].toRating().toStarRating(),
            cursor[MediaTable.playedCount],
            Millis(cursor[MediaTable.lastPlayedTime]),
            cursor[MediaTable.skippedCount],
            Millis(cursor[MediaTable.lastSkippedTime]),
            Millis(cursor[MediaTable.createdTime]),
            cursor[MediaTable.location].toUriOrEmpty(),
            cursor[MediaTable.fileUri].toUriOrEmpty(),
            AlbumId(cursor[MediaTable.albumId]),
            ArtistId(cursor[MediaTable.albumArtistId])
          )
        }
        .single()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }


  override suspend fun getTitleSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      MediaTable
        .select { title }
        .where { title like textSearch.applyWildcards(partialTitle) escape ESC_CHAR }
        .sequence { it[title] }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

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
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      SongArtistTable
        .join(MediaTable, INNER, songArtistTableId, MediaTable.artistId)
        .select { songArtistTableName }
        .where { songArtistTableName like textSearch.applyWildcards(partial) escape ESC_CHAR }
        .distinct()
        .sequence { it[songArtistTableName] }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getAlbumArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> = runSuspendCatching {
    db.query {
      AlbumArtistTable
        .join(MediaTable, INNER, albumArtistTableId, MediaTable.albumArtistId)
        .select { albumArtistTableName }
        .where { albumArtistTableName like textSearch.applyWildcards(partial) escape ESC_CHAR }
        .distinct()
        .sequence { it[albumArtistTableName] }
        .toList()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

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
    appPrefs: AppPrefs,
    upsertResults: AudioUpsertResults
  ): MediaId {
    val formatFromExt = MediaFormat.mediaFormatFromExtension(audioInfo.extension)
    return maybeUpdateAudioMedia(
      audioInfo,
      newAlbumId,
      newAlbumArtistId,
      newArtistId,
      fileTagInfo,
      createUpdateTime,
      appPrefs,
      upsertResults
    ) ?: INSERT_AUDIO_STATEMENT.insert {
      it[location] = audioInfo.location.toString()
      it[fileUri] = audioInfo.path.toUri().toString()
      it[displayName] = audioInfo.displayName
      it[mediaType] = MediaType.Audio.id
      it[mediaFormat] = formatFromExt.id
      it[title] = fileTagInfo.title
      it[titleSort] = fileTagInfo.titleSort
      it[albumId] = newAlbumId.value
      it[albumArtistId] = newAlbumArtistId.value
      it[artistId] = newArtistId.value
      it[genre] = fileTagInfo.genre
      it[year] = fileTagInfo.year
      if (appPrefs.readTagRating()) it[rating] = fileTagInfo.rating.toRating().value
      it[duration] = fileTagInfo.duration()
      it[trackNumber] = fileTagInfo.trackNumber
      it[totalTracks] = fileTagInfo.totalTracks
      it[discNumber] = fileTagInfo.discNumber
      it[totalDiscs] = fileTagInfo.totalDiscs
      it[comment] = fileTagInfo.comment
      it[trackMbid] = fileTagInfo.trackMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
    }.asMediaId.also { id -> upsertResults.mediaCreated(id) }
  }

  private fun Transaction.maybeUpdateAudioMedia(
    newInfo: AudioInfo,
    newAlbumId: AlbumId,
    newAlbumArtistId: ArtistId,
    newArtistId: ArtistId,
    fileTagInfo: MediaFileTagInfo,
    newUpdateTime: Millis,
    appPrefs: AppPrefs,
    upsertResults: AudioUpsertResults
  ): MediaId? = queryAudioForUpdate(newInfo.location)?.let { info ->
    val updateTitle = info.title.updateOrNull { fileTagInfo.title }
    val updateTitleSort = info.titleSort.updateOrNull { fileTagInfo.titleSort }
    val updateAlbumId = info.albumId.updateOrNull { newAlbumId }
    val updateAlbumArtistId = info.albumArtistId.updateOrNull { newAlbumArtistId }
    val updateArtistId = info.artistId.updateOrNull { newArtistId }
    val updateGenre = info.genre.updateOrNull { fileTagInfo.genre }
    val updateYear = info.year.updateOrNull { fileTagInfo.year }
    val updateRating = if (appPrefs.readTagRating())
      info.rating.updateOrNull { fileTagInfo.rating } else null
    val updateDuration = info.duration.updateOrNull { fileTagInfo.duration }
    val updateTrackNumber = info.trackNumber.updateOrNull { fileTagInfo.trackNumber }
    val updateTotalTracks = info.totalTracks.updateOrNull { fileTagInfo.totalTracks }
    val updateDiscNumber = info.discNumber.updateOrNull { fileTagInfo.discNumber }
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
        updateGenre,
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
        updateGenre?.let { update -> it[genre] = update }
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

      if (updated >= 1) upsertResults.mediaUpdated(info.id)
      else LOG.e { it("Could not update ${info.title}") }
    }
    info.id
  }

  private fun Queryable.queryAudioForUpdate(location: Uri): AudioForUpdate? = QUERY_AUDIO_FOR_UPDATE
    .sequence({ bindings -> bindings[0] = location.toString() }) { cursor ->
      AudioForUpdate(
        MediaId(cursor[id]),
        cursor[title],
        cursor[titleSort],
        AlbumId(cursor[albumId]),
        ArtistId(cursor[albumArtistId]),
        ArtistId(cursor[artistId]),
        cursor[genre],
        cursor[year],
        Rating(cursor[rating]).toStarRating(),
        Millis(cursor[duration]),
        cursor[trackNumber],
        cursor[totalTracks],
        cursor[discNumber],
        cursor[totalDiscs],
        cursor[comment],
        cursor[trackMbid].toTrackMbidOrNull()
      )
    }.singleOrNull()

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

  companion object {
    val AlbumArtistTable = ArtistTable.alias("AlbumArtist")
    val SongArtistTable = ArtistTable.alias("SongArtist")

    val albumArtistTableId = AlbumArtistTable[ArtistTable.id]
    val albumArtistTableName = AlbumArtistTable[ArtistTable.artistName]
    val songArtistTableId = SongArtistTable[ArtistTable.id]
    val songArtistTableName = SongArtistTable[ArtistTable.artistName]
  }
}

private val INSERT_AUDIO_STATEMENT = MediaTable.insertValues {
  it[location].bindArg()
  it[fileUri].bindArg()
  it[displayName].bindArg()
  it[mediaType].bindArg()
  it[mediaFormat].bindArg()
  it[title].bindArg()
  it[titleSort].bindArg()
  it[albumId].bindArg()
  it[albumArtistId].bindArg()
  it[artistId].bindArg()
  it[genre].bindArg()
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
  val genre: String,
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

private val AudioViewQueueDataQuery: QueryBuilder<Join> = MediaTable
  .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
  .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
  .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
  .selects {
    listOf(
      MediaTable.id,
      MediaTable.location,
      MediaTable.fileUri,
      MediaTable.displayName,
      MediaTable.title,
      AlbumTable.albumTitle,
      AlbumTable.id,
      MediaTable.rating,
      MediaTable.duration,
      MediaTable.trackNumber,
      albumArtistTableName,
      songArtistTableName,
      AlbumTable.albumLocalArtUri,
      AlbumTable.albumArtUri
    )
  }
  .where { MediaTable.mediaType eq MediaType.Audio.id }

object AudioViewQueueData : View(AudioViewQueueDataQuery) {
  val mediaId = column(MediaTable.id)
  val mediaLocation = column(MediaTable.location)
  val mediaFileUri = column(MediaTable.fileUri)
  val mediaDisplayName = column(MediaTable.displayName)
  val mediaTitle = column(MediaTable.title)
  val albumTitle = column(AlbumTable.albumTitle)
  val albumId = column(AlbumTable.id)
  val rating = column(MediaTable.rating)
  val duration = column(MediaTable.duration)
  val trackNumber = column(MediaTable.trackNumber)
  val albumArtistName = column(albumArtistTableName, "AlbumArtist")
  val songArtistName = column(songArtistTableName, "SongArtist")
  val localAlbumArt = column(AlbumTable.albumLocalArtUri)
  val albumArt = column(AlbumTable.albumArtUri)
}

//private val FullAudioViewQuery = MediaTable
//  .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
//  .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
//  .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
//  .join(GenreMediaTable, INNER, MediaTable.id, GenreMediaTable.mediaId)
//  .join(GenreTable, LEFT, GenreTable.id, GenreMediaTable.genreId)
//  .join(ComposerMediaTable, INNER, MediaTable.id, ComposerMediaTable.mediaId)
//  .join(ComposerTable, LEFT, ComposerTable.id, ComposerMediaTable.composerId)
//  .selects {
//    listOf(
//      MediaTable.id,
//      MediaTable.title,
//      AlbumTable.albumTitle,
//      albumArtistTableName,
//      SongArtistTable[ArtistTable.artistName],
//      GenreTable.genre,
//      ComposerTable.composer
//    )
//  }
//  .where { MediaTable.mediaType eq MediaType.Audio.id }
//
//object FullAudioView : View(FullAudioViewQuery) {
//  val mediaId = column(MediaTable.id)
//  val mediaTitle = column(MediaTable.title)
//  val albumTitle = column(AlbumTable.albumTitle)
//  val albumArtistName = column(albumArtistTableName)
//  val songArtistName = column(SongArtistTable[ArtistTable.artistName])
//  val genreName = column(GenreTable.genre)
//  val composeName = column(ComposerTable.composer)
//}

private val SINGLE_QUEUE_ITEM_DATA_QUERY by lazy {
  Query(
    MediaTable
      .join(AlbumTable, INNER, MediaTable.albumId, AlbumTable.id)
      .join(AlbumArtistTable, INNER, MediaTable.albumArtistId, albumArtistTableId)
      .join(SongArtistTable, INNER, MediaTable.artistId, songArtistTableId)
      .selects {
        listOf(
          MediaTable.id,
          MediaTable.location,
          MediaTable.fileUri,
          MediaTable.displayName,
          MediaTable.title,
          AlbumTable.albumTitle,
          AlbumTable.id,
          MediaTable.rating,
          MediaTable.duration,
          MediaTable.trackNumber,
          albumArtistTableName,
          songArtistTableName
        )
      }
      .where { (MediaTable.mediaType eq MediaType.Audio.id) and (MediaTable.id eq bindLong()) }
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
    it[playedCount] = playedCount plus 1
    it[lastPlayedTime] = BIND_CURRENT_TIME
  }.where {
    id eq BIND_MEDIA_ID
  }
}

private val AUDIO_DESCRIPTION_SELECTS = listOf(
  MediaTable.id,
  MediaTable.title,
  MediaTable.duration,
  MediaTable.rating,
  AlbumTable.albumTitle,
  AlbumTable.albumLocalArtUri,
  AlbumTable.albumArtUri,
  ArtistTable.artistName,
)

private val BIND_FILTER_ONE = bindString()
private val BIND_FILTER_TWO = bindString()
private val BIND_FILTER_THREE = bindString()
private val AUDIO_LIKE_CONDITION = (MediaTable.title like BIND_FILTER_ONE escape ESC_CHAR) or
    (AlbumTable.albumTitle like BIND_FILTER_TWO escape ESC_CHAR) or
    (ArtistTable.artistName like BIND_FILTER_THREE escape ESC_CHAR)

private fun Op<Boolean>.filter(filter: Filter): Op<Boolean> =
  if (filter.isEmpty) this else this and AUDIO_LIKE_CONDITION

private fun ArgBindings.filter(filter: Filter): ArgBindings = apply {
  if (filter.isNotEmpty) {
    this[BIND_FILTER_ONE] = filter.value
    this[BIND_FILTER_TWO] = filter.value
    this[BIND_FILTER_THREE] = filter.value
  }
}

private fun Op<Boolean>.restrictTo(artistId: ArtistId?): Op<Boolean> =
  if (artistId == null) this else this and (MediaTable.artistId eq artistId.value)

private fun AudioDescription(cursor: Cursor): AudioDescription = AudioDescription(
  cursor[MediaTable.id].asMediaId,
  cursor[MediaTable.title].asTitle,
  Millis(cursor[MediaTable.duration]),
  Rating(cursor[MediaTable.rating]),
  AlbumTitle(cursor[AlbumTable.albumTitle]),
  ArtistName(cursor[ArtistTable.artistName]),
  cursor[AlbumTable.albumLocalArtUri].toUriOrEmpty(),
  cursor[AlbumTable.albumArtUri].toUriOrEmpty()
)
