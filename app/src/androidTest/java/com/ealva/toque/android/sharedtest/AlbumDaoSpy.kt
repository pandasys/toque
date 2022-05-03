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

package com.ealva.toque.android.sharedtest

import android.net.Uri
import com.ealva.ealvabrainz.brainz.data.ReleaseGroupMbid
import com.ealva.ealvabrainz.brainz.data.ReleaseMbid
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDaoEvent
import com.ealva.toque.db.AlbumDescription
import com.ealva.toque.db.AudioUpsertResults
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.TextSearch
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ComposerId
import com.ealva.toque.persist.GenreId
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.ui.library.ArtistType
import com.ealva.welite.db.TransactionInProgress
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.SharedFlow

@Suppress("PropertyName")
class AlbumDaoSpy : AlbumDao {
  override val albumDaoEvents: SharedFlow<AlbumDaoEvent>
    get() = TODO("Not yet implemented")

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
  ): AlbumId {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteAll(): Long {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteAlbumsWithNoMedia(): Long {
    TODO("Not yet implemented")
  }

  var _getAllAlbumsCalled: Int = 0
  var _getAllAlbumsFilter: Filter? = null
  var _getAllAlbumsLimit: Limit? = null
  var _getAllAlbumsReturn: Result<List<AlbumDescription>, Throwable>? = null
  override suspend fun getAllAlbums(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AlbumDescription>> {
    _getAllAlbumsCalled++
    _getAllAlbumsFilter = filter
    _getAllAlbumsLimit = limit
    return checkNotNull(_getAllAlbumsReturn)
  }

  override suspend fun getAllAlbumsFor(
    artistId: ArtistId,
    artistType: ArtistType,
    filter: Filter,
    limit: Limit
  ): DaoResult<List<AlbumDescription>> {
    TODO("Not yet implemented")
  }

  override suspend fun getNext(albumId: AlbumId): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getPrevious(albumId: AlbumId): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMin(): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMax(): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getRandom(): DaoResult<AlbumId> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumSuggestions(
    partialTitle: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }

  override suspend fun downloadArt(albumId: AlbumId, block: (AlbumDao) -> Unit) {
    TODO("Not yet implemented")
  }

  override fun setAlbumArt(albumId: AlbumId, remote: Uri, local: Uri) {
    TODO("Not yet implemented")
  }

  override suspend fun getArtistAlbum(albumId: AlbumId): DaoResult<Pair<ArtistName, AlbumTitle>> {
    TODO("Not yet implemented")
  }

  var _getAlbumArtForArtistCalled: Int = 0
  var _getAlbumArtForArtistArtistId: ArtistId? = null
  var _getAlbumArtForArtistArtistType: ArtistType? = null
  var _getAlbumArtForArtistReturn: Result<Uri, Throwable>? = null
  override suspend fun getAlbumArtFor(artistId: ArtistId, artistType: ArtistType): DaoResult<Uri> {
    _getAlbumArtForArtistCalled++
    _getAlbumArtForArtistArtistId = artistId
    _getAlbumArtForArtistArtistType = artistType
    return checkNotNull(_getAlbumArtForArtistReturn)
  }

  var _getAlbumArtForGenreCalled: Int = 0
  var _getAlbumArtForGenreGenreId: GenreId? = null
  var _getAlbumArtForGenreReturn: Result<Uri, Throwable>? = null
  override suspend fun getAlbumArtFor(genreId: GenreId): DaoResult<Uri> {
    _getAlbumArtForGenreCalled++
    _getAlbumArtForGenreGenreId = genreId
    return checkNotNull(_getAlbumArtForGenreReturn)
  }

  var _getAlbumArtForComposerCalled: Int = 0
  var _getAlbumArtForComposerComposerId: ComposerId? = null
  var _getAlbumArtForComposerReturn: Result<Uri, Throwable>? = null
  override suspend fun getAlbumArtFor(composerId: ComposerId): DaoResult<Uri> {
    _getAlbumArtForComposerCalled++
    _getAlbumArtForComposerComposerId = composerId
    return checkNotNull(_getAlbumArtForComposerReturn)
  }

  var _getAlbumArtForPlaylistCalled: Int = 0
  var _getAlbumArtForPlaylistPlaylistId: PlaylistId? = null
  var _getAlbumArtForPlaylistReturn: Result<Uri, Throwable>? = null
  override suspend fun getAlbumArtFor(
    playlistId: PlaylistId,
    playlistType: PlayListType,
    playlistName: PlaylistName
  ): DaoResult<Uri> {
    _getAlbumArtForPlaylistCalled++
    _getAlbumArtForPlaylistPlaylistId = playlistId
    return checkNotNull(_getAlbumArtForPlaylistReturn)
  }

  override suspend fun getAlbumYear(albumId: AlbumId): Int {
    TODO("Not yet implemented")
  }
}
