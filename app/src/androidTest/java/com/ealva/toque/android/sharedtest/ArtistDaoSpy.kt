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
import com.github.michaelbull.result.Result
import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.toque.common.EntityArtwork
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.ArtistDaoEvent
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.db.ArtistIdName
import com.ealva.toque.db.AudioUpsertResults
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.TextSearch
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.ArtistIdList
import com.ealva.toque.persist.MediaId
import com.ealva.welite.db.TransactionInProgress
import kotlinx.coroutines.flow.SharedFlow

@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
class ArtistDaoSpy : ArtistDao {
  override val artistDaoEvents: SharedFlow<ArtistDaoEvent>
    get() = TODO("Not yet implemented")

  override fun TransactionInProgress.upsertArtist(
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteAll(): Long {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.deleteArtistsWithNoMedia(): Long {
    TODO("Not yet implemented")
  }

  var _getAlbumArtistsCount: Int = 0
  var _getAlbumArtistsFilter: Filter? = null
  var _getAlbumArtistsLimit: Limit? = null
  var _getAlbumArtistsReturn: Result<List<ArtistDescription>, Throwable>? = null
  override suspend fun getAlbumArtists(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ArtistDescription>> {
    _getAlbumArtistsCount++
    _getAlbumArtistsFilter = filter
    _getAlbumArtistsLimit = limit
    return checkNotNull(_getAlbumArtistsReturn)
  }

  var _getSongArtistsCount: Int = 0
  var _getSongArtistsFilter: Filter? = null
  var _getSongArtistsLimit: Limit? = null
  var _getSongArtistsReturn: Result<List<ArtistDescription>, Throwable>? = null
  override suspend fun getSongArtists(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<ArtistDescription>> {
    _getSongArtistsCount++
    _getSongArtistsFilter = filter
    _getSongArtistsLimit = limit
    return checkNotNull(_getSongArtistsReturn)
  }

  override suspend fun getAllArtistNames(limit: Limit): DaoResult<List<ArtistIdName>> {
    TODO("Not yet implemented")
  }

  override suspend fun getNext(id: ArtistId): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getPrevious(id: ArtistId): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMin(): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMax(): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getRandom(): DaoResult<ArtistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }

  override suspend fun getAlbumArtistSuggestions(
    partial: String,
    textSearch: TextSearch
  ): DaoResult<List<String>> {
    TODO("Not yet implemented")
  }

  override fun TransactionInProgress.replaceMediaArtists(
    artistIdList: ArtistIdList,
    replaceMediaId: MediaId,
    createTime: Millis
  ) {
    TODO("Not yet implemented")
  }

  override suspend fun downloadArt(artistId: ArtistId, block: (ArtistDao) -> Unit) {
    TODO("Not yet implemented")
  }

  override fun setArtistArt(artistId: ArtistId, remote: Uri, local: Uri) {
    TODO("Not yet implemented")
  }

  override suspend fun getArtistName(artistId: ArtistId): DaoResult<ArtistName> {
    TODO("Not yet implemented")
  }

  override suspend fun getArtwork(artistId: ArtistId): DaoResult<EntityArtwork> {
    TODO("Not yet implemented")
  }
}
