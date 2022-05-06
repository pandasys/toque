/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.sharedtest

import android.net.Uri
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Rating
import com.ealva.toque.common.ShuffleLists
import com.ealva.toque.common.Title
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.AudioDaoEvent
import com.ealva.toque.db.AudioDescription
import com.ealva.toque.db.AudioItemListResult
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.ComposerDao
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.FullAudioInfo
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.TextSearch
import com.ealva.toque.file.AudioInfo
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
import com.ealva.toque.service.media.MediaMetadataParser
import com.github.michaelbull.result.Result
import it.unimi.dsi.fastutil.longs.LongCollection
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

@Suppress("PropertyName")
class AudioMediaDaoSpy : AudioMediaDao {
  override lateinit var audioDaoEvents: Flow<AudioDaoEvent>
  override lateinit var albumDao: AlbumDao
  override lateinit var artistDao: ArtistDao
  override lateinit var genreDao: GenreDao
  override lateinit var composerDao: ComposerDao
  override lateinit var playlistDao: PlaylistDao

  override suspend fun upsertAudio(
    audioInfo: AudioInfo,
    metadataParser: MediaMetadataParser,
    minimumDuration: Duration,
    createUpdateTime: Millis
  ) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteAll() {
    TODO("Not yet implemented")
  }

  override suspend fun deleteEntitiesWithNoMedia() {
    TODO("Not yet implemented")
  }

  var _getAllAudioCalled: Int = 0
  var _getAllAudioFilter: Filter? = null
  var _getAllAudioLimit: Limit? = null
  var _getAllAudioReturn: Result<List<AudioDescription>, Throwable>? = null
  override suspend fun getAllAudio(
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    _getAllAudioCalled++
    _getAllAudioFilter = filter
    _getAllAudioLimit = limit
    return checkNotNull(_getAllAudioReturn)
  }

  override suspend fun getArtistAudio(
    id: ArtistId,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumArtistAudio(
    id: ArtistId,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumAudio(
    id: AlbumId,
    restrictTo: ArtistId?,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getGenreAudio(
    genreId: GenreId,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getComposerAudio(
    composerId: ComposerId,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getPlaylistAudio(
    playlistId: PlaylistId,
    playListType: PlayListType,
    filter: Filter,
    limit: Limit
  ): Result<List<AudioDescription>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getAudioQueueItems(shuffled: Boolean): AudioItemListResult {
    TODO("Not yet implemented")
  }

  override suspend fun <T : HasId> makeShuffledQueue(upNextQueue: List<T>): MutableList<T> {
    TODO("Not yet implemented")
  }

  override suspend fun getAudioItemsForQueue(idList: LongCollection): AudioItemListResult {
    TODO("Not yet implemented")
  }

  override fun incrementPlayedCount(id: MediaId) {
    TODO("Not yet implemented")
  }

  override fun incrementSkippedCount(id: MediaId) {
    TODO("Not yet implemented")
  }

  override fun updateDuration(id: MediaId, newDuration: Duration) {
    TODO("Not yet implemented")
  }

  override suspend fun setRating(id: MediaId, newRating: Rating): DaoResult<Rating> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaTitle(mediaId: MediaId): DaoResult<Title> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForAlbums(
    albumsIds: AlbumIdList,
    restrictTo: ArtistId?,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForArtists(
    artistIds: ArtistIdList,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForComposers(
    composerIds: ComposerIdList,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForGenres(
    genreIds: GenreIdList,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    removeDuplicates: Boolean,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getNextCategory(
    token: CategoryToken,
    shuffleLists: ShuffleLists
  ): CategoryMediaList {
    TODO("Not yet implemented")
  }

  override suspend fun getPreviousCategory(
    token: CategoryToken,
    shuffleLists: ShuffleLists
  ): CategoryMediaList {
    TODO("Not yet implemented")
  }

  override suspend fun getFullInfo(mediaId: MediaId): DaoResult<FullAudioInfo> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumId(mediaId: MediaId): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getArtistId(mediaId: MediaId): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumArtistId(mediaId: MediaId): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override fun setAlbumRemoteArt(mediaId: MediaId, location: Uri) {
    TODO("Not yet implemented")
  }

  override suspend fun getTitleSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }
}
