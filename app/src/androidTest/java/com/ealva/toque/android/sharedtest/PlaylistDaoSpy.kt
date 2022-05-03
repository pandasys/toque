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

import com.ealva.toque.common.Filter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.Memento
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.PlaylistDaoEvent
import com.ealva.toque.db.PlaylistDescription
import com.ealva.toque.db.PlaylistIdNameType
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.ealva.welite.db.Queryable
import kotlinx.coroutines.flow.SharedFlow
import com.github.michaelbull.result.Result

@Suppress("PropertyName")
class PlaylistDaoSpy : PlaylistDao {
  override val playlistDaoEvents: SharedFlow<PlaylistDaoEvent>
    get() = TODO("Not yet implemented")

  override suspend fun getAllOfType(
    vararg types: PlayListType
  ): DaoResult<List<PlaylistIdNameType>> {
    TODO("Not yet implemented")
  }

  override suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): DaoResult<PlaylistIdNameType> {
    TODO("Not yet implemented")
  }

  override suspend fun createOrUpdateSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ): DaoResult<SmartPlaylist> {
    TODO("Not yet implemented")
  }

  override suspend fun deletePlaylist(playlistId: PlaylistId): DaoResult<Memento> {
    TODO("Not yet implemented")
  }

  override suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    limit: Limit
  ): DaoResult<MediaIdList> {
    TODO("Not yet implemented")
  }

  override suspend fun getSmartPlaylist(playlistId: PlaylistId): DaoResult<SmartPlaylist> {
    TODO("Not yet implemented")
  }

  override suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): DaoResult<Long> {
    TODO("Not yet implemented")
  }

  var _getAllPlaylistsCalled: Int = 0
  var _getAllPlaylistsFilter: Filter? = null
  var _getAllPlaylistsLimit: Limit? = null
  var _getAllPlaylistsResult: Result<List<PlaylistDescription>, Throwable>? = null
  override suspend fun getAllPlaylists(
    filter: Filter,
    limit: Limit
  ): DaoResult<List<PlaylistDescription>> {
    _getAllPlaylistsCalled++
    _getAllPlaylistsFilter = filter
    _getAllPlaylistsLimit = limit
    return checkNotNull(_getAllPlaylistsResult)
  }

  override fun Queryable.getPlaylistName(playlistId: PlaylistId, type: PlayListType): PlaylistName {
    TODO("Not yet implemented")
  }

  override suspend fun getNext(playlistId: PlaylistId): DaoResult<PlaylistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getPrevious(playlistId: PlaylistId): DaoResult<PlaylistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMin(): DaoResult<PlaylistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getMax(): DaoResult<PlaylistId> {
    TODO("Not yet implemented")
  }

  override suspend fun getRandom(): DaoResult<PlaylistId> {
    TODO("Not yet implemented")
  }

  override suspend fun smartPlaylistsReferringTo(playlistId: PlaylistId): PlaylistIdList {
    TODO("Not yet implemented")
  }

  override suspend fun getDescription(persistentId: PlaylistId): PlaylistDescription {
    TODO("Not yet implemented")
  }
}
