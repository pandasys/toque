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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asPlaylistId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError

private val LOG by lazyLogger(PlaylistDao::class)

data class PlaylistIdName(
  val id: PlaylistId,
  val name: PlaylistName
)

data class PlaylistDescription(
  val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType,
  val songCount: Long
)

interface PlaylistDao {
  suspend fun getUserPlaylistNames(): Result<List<PlaylistIdName>, DaoMessage>

  suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): Result<PlaylistIdName, DaoMessage>

  suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): Result<Long, DaoMessage>

  suspend fun getAllPlaylists(): Result<List<PlaylistDescription>, DaoMessage>

  suspend fun getNext(playlistId: PlaylistId): Result<PlaylistId, DaoMessage>
  suspend fun getPrevious(playlistId: PlaylistId): Result<PlaylistId, DaoMessage>
  suspend fun getMin(): Result<PlaylistId, DaoMessage>
  suspend fun getMax(): Result<PlaylistId, DaoMessage>
  suspend fun getRandom(): Result<PlaylistId, DaoMessage>

  companion object {
    operator fun invoke(db: Database): PlaylistDao = PlaylistDaoImpl(db)
  }
}

private class PlaylistDaoImpl(private val db: Database) : PlaylistDao {
  override suspend fun getUserPlaylistNames(): Result<List<PlaylistIdName>, DaoMessage> =
    runSuspendCatching {
      db.query {
        PlayListTable.selects { listOf(id, playListName) }
          .all()
          .sequence {
            PlaylistIdName(
              it[id].asPlaylistId,
              it[playListName].asPlaylistName
            )
          }.toList()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): Result<PlaylistIdName, DaoMessage> = runSuspendCatching {
    db.transaction {
      val playlistId = PlayListTable.insert {
        it[playListName] = name.value
        it[playListType] = PlayListType.UserCreated.id
        it[createdTime] = Millis.currentTime().value
        it[sort] = PlayListType.UserCreated.sortPosition
      }.asPlaylistId

      if (playlistId >= 1) {
        doAddMediaToPlaylist(mediaIdList, playlistId, 1)
      } else throw IllegalStateException("Could not create $name")
      PlaylistIdName(playlistId, name)
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): Result<Long, DaoMessage> = runSuspendCatching {
    db.transaction { doAddMediaToPlaylist(mediaIdList, id, getNextSortValue(id)) }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getAllPlaylists(): Result<List<PlaylistDescription>, DaoMessage> =
    runSuspendCatching {
      db.query {
        val songCountColumn = PlayListMediaTable.mediaId.count()
        mutableListOf<PlaylistDescription>().apply {
          PlayListTable
            .join(
              PlayListMediaTable,
              JoinType.INNER,
              PlayListTable.id,
              PlayListMediaTable.playListId
            )
            .selects {
              listOf(PlayListTable.id, PlayListTable.playListName, songCountColumn)
            }
            .where { PlayListTable.playListType eq PlayListType.UserCreated.id }
            .orderByAsc { PlayListTable.playListName }
            .groupBy { PlayListTable.playListName }
            .sequence {
              PlaylistDescription(
                PlaylistId(it[PlayListTable.id]),
                it[PlayListTable.playListName].asPlaylistName,
                PlayListType.UserCreated,
                it[songCountColumn]
              )
            }
            .forEach { add(it) }
        }
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getNext(playlistId: PlaylistId) = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { playListName greater SELECT_PLAYLIST_FROM_BIND_ID }
        .orderByAsc { playListName }
        .limit(1)
        .longForQuery { it[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getPrevious(playlistId: PlaylistId) = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { playListName less SELECT_PLAYLIST_FROM_BIND_ID }
        .orderBy { playListName by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }.mapError { DaoExceptionMessage(it) }

  private val playlistMin by lazy { PlayListTable.playListName.min().alias("playlist_min_alias") }
  override suspend fun getMin(): Result<PlaylistId, DaoExceptionMessage> = runSuspendCatching {
    db.query {
      PlayListTable
        .selects { listOf(id, playlistMin) }
        .all()
        .limit(1)
        .sequence { PlaylistId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  private val playlistMax by lazy { PlayListTable.playListName.max().alias("playlist_max_alias") }
  override suspend fun getMax() = runSuspendCatching {
    db.query {
      AlbumTable
        .selects { listOf(id, playlistMax) }
        .all()
        .limit(1)
        .sequence { PlaylistId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getRandom(): Result<PlaylistId, DaoMessage> = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { id inSubQuery PlayListTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asPlaylistId
    }
  }.mapError { DaoExceptionMessage(it) }

  private fun TransactionInProgress.doAddMediaToPlaylist(
    mediaIdList: MediaIdList,
    playlistId: PlaylistId,
    startingSortValue: Long
  ): Long {
    var count = 0L
    var sortValue = startingSortValue
    mediaIdList.forEach { mediaId ->
      if (INSERT_PLAYLIST_MEDIA.insert {
          it[BIND_PLAYLIST_ID] = playlistId.value
          it[BIND_MEDIA_ID] = mediaId.value
          it[BIND_SORT_VALUE] = sortValue++
        } >= 0) {
        count++
      } else {
        LOG.e { it("Failed to insert %s %s sort:%d", playlistId, mediaId, sortValue - 1) }
      }
    }
    return count
  }

  private fun Queryable.getNextSortValue(id: PlaylistId): Long {
    val max = PlayListMediaTable.select(PlayListMediaTable.sort.max())
      .where { playListId eq id.value }
      .longForQuery()
    return if (max <= 0) 1 else max
  }
}

private val BIND_PLAYLIST_ID = bindLong()
private val BIND_MEDIA_ID = bindLong()
private val BIND_SORT_VALUE = bindLong()
private val INSERT_PLAYLIST_MEDIA = PlayListMediaTable.insertValues {
  it[playListId] = BIND_PLAYLIST_ID
  it[mediaId] = BIND_MEDIA_ID
  it[sort] = BIND_SORT_VALUE
}

private val SELECT_PLAYLIST_FROM_BIND_ID: Expression<String> = AlbumTable
  .select(PlayListTable.playListName)
  .where { id eq BIND_PLAYLIST_ID }
  .asExpression()
