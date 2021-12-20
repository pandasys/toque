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
import com.ealva.toque.db.PlaylistDaoEvent.PlaylistCreated
import com.ealva.toque.db.PlaylistDaoEvent.PlaylistUpdated
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.db.smart.SmartPlaylistDao
import com.ealva.toque.db.smart.SmartPlaylistTable
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.asPlaylistId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
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
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.existingView
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

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


sealed interface PlaylistDaoEvent {
  data class PlaylistCreated(val playlistId: PlaylistId) : PlaylistDaoEvent
  data class PlaylistUpdated(val playlistId: PlaylistId) : PlaylistDaoEvent
  data class PlaylistDeleted(val playlistId: PlaylistId) : PlaylistDaoEvent
}

interface PlaylistDao {
  val playlistDaoEvents: SharedFlow<PlaylistDaoEvent>

  suspend fun getUserPlaylistNames(): Result<List<PlaylistIdName>, DaoMessage>
  suspend fun getAllPlaylistNames(): Result<List<PlaylistName>, DaoMessage>

  suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): Result<PlaylistIdName, DaoMessage>

  suspend fun createOrUpdateSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ): Result<SmartPlaylist, DaoMessage>

  suspend fun getSmartPlaylist(playlistId: PlaylistId): Result<SmartPlaylist, DaoMessage>

  suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): Result<Long, DaoMessage>

  suspend fun getAllPlaylists(): Result<List<PlaylistDescription>, DaoMessage>

  fun Queryable.getPlaylistName(playlistId: PlaylistId, type: PlayListType): PlaylistName

  suspend fun getNext(playlistId: PlaylistId): Result<PlaylistId, DaoMessage>
  suspend fun getPrevious(playlistId: PlaylistId): Result<PlaylistId, DaoMessage>
  suspend fun getMin(): Result<PlaylistId, DaoMessage>
  suspend fun getMax(): Result<PlaylistId, DaoMessage>
  suspend fun getRandom(): Result<PlaylistId, DaoMessage>

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): PlaylistDao = PlaylistDaoImpl(db, dispatcher)
  }
}

private class PlaylistDaoImpl(
  private val db: Database,
  dispatcher: CoroutineDispatcher
) : PlaylistDao {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val smartPlaylistDao: SmartPlaylistDao = SmartPlaylistDao(db)

  override val playlistDaoEvents = MutableSharedFlow<PlaylistDaoEvent>()

  private fun emit(event: PlaylistDaoEvent) {
    scope.launch { playlistDaoEvents.emit(event) }
  }

  override suspend fun getUserPlaylistNames(): Result<List<PlaylistIdName>, DaoMessage> =
    runSuspendCatching {
      db.query {
        PlayListTable.selects { listOf(id, playListName) }
          .all()
          .sequence { cursor ->
            PlaylistIdName(cursor[id].asPlaylistId, cursor[playListName].asPlaylistName)
          }
          .toList()
      }
    }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getAllPlaylistNames(): Result<List<PlaylistName>, DaoMessage> =
    runSuspendCatching {
      db.query {
        PlayListTable.select { playListName }
          .all()
          .sequence { cursor -> cursor[playListName].asPlaylistName }
          .toList()
      }
    }
      .mapError { cause -> DaoExceptionMessage(cause) }

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
  }
    .mapError { cause -> DaoExceptionMessage(cause) }
    .onSuccess { playlistIdName -> emit(PlaylistCreated(playlistId = playlistIdName.id)) }

  override suspend fun createOrUpdateSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ): Result<SmartPlaylist, DaoMessage> = runSuspendCatching {
    db.transaction {
      if (smartPlaylist.id.value < 1 || !playlistExists(smartPlaylist.id)) {
        with(smartPlaylistDao) { createPlaylist(createRulesPlaylist(smartPlaylist)) }
      } else {
        updatePlaylistTime(smartPlaylist.id, Millis.currentTime())
        with(smartPlaylistDao) { updatePlaylist(smartPlaylist) }
      }
    }
  }.mapError { cause ->
    DaoExceptionMessage(cause)
  }.onSuccess { result ->
    emit(if (smartPlaylist.id < 0) PlaylistCreated(result.id) else PlaylistUpdated(result.id))
  }

  private fun TransactionInProgress.playlistExists(playlistId: PlaylistId): Boolean {
    return PlayListTable
      .select { id }
      .where { id eq playlistId.value }
      .longForQuery() > 0
  }

  override suspend fun getSmartPlaylist(playlistId: PlaylistId): Result<SmartPlaylist, DaoMessage> =
    smartPlaylistDao.getPlaylist(playlistId)

  private fun TransactionInProgress.createRulesPlaylist(
    playlist: SmartPlaylist
  ): SmartPlaylist = playlist.copy(
    id = PlayListTable.insert {
      it[playListName] = playlist.name.value
      it[playListType] = PlayListType.Rules.id
      it[createdTime] = Millis.currentTime().value
      it[sort] = PlayListType.UserCreated.sortPosition
    }.asPlaylistId
  )

  private fun TransactionInProgress.updatePlaylistTime(
    playlistId: PlaylistId,
    updateTime: Millis
  ): Long = PlayListTable
    .updateColumns { it[updatedTime] = updateTime.value }
    .where { id eq playlistId.value }
    .update()

  override suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): Result<Long, DaoMessage> =
    runSuspendCatching {
      db.transaction {
        doAddMediaToPlaylist(mediaIdList, id, getNextSortValue(id))
        updatePlaylistTime(id, Millis.currentTime())
      }
    }
      .mapError { cause -> DaoExceptionMessage(cause) }
      .onSuccess { emit(PlaylistUpdated(id)) }


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
            .groupBy { PlayListTable.playListName }
            .sequence { cursor ->
              PlaylistDescription(
                PlaylistId(cursor[PlayListTable.id]),
                cursor[PlayListTable.playListName].asPlaylistName,
                PlayListType.UserCreated,
                cursor[songCountColumn]
              )
            }
            .forEach { add(it) }

          PlayListTable
            .join(
              SmartPlaylistTable,
              JoinType.INNER,
              PlayListTable.id,
              SmartPlaylistTable.smartId
            )
            .selects {
              listOf(PlayListTable.id, PlayListTable.playListName, SmartPlaylistTable.smartName)
            }
            .where { PlayListTable.playListType eq PlayListType.Rules.id }
            .sequence { cursor ->
              SmartPlaylistDescription(
                PlaylistId(cursor[PlayListTable.id]),
                cursor[PlayListTable.playListName].asPlaylistName,
                cursor[SmartPlaylistTable.smartName]
              )
            }
            .map { smart -> getSmartPlaylistSongCount(smart) }
            .forEach { add(it) }
        }
      }
    }.mapError { cause -> DaoExceptionMessage(cause) }

  private fun Queryable.getSmartPlaylistSongCount(
    smart: SmartPlaylistDescription
  ): PlaylistDescription {
    val view = existingView(smart.viewName)
    val viewId = view.column(MediaTable.id, MediaTable.id.name)

    return PlaylistDescription(
      id = smart.id,
      name = smart.name,
      type = PlayListType.Rules,
      songCount = view
        .join(MediaTable, JoinType.INNER, viewId, MediaTable.id)
        .selectCount()
        .longForQuery()
    )
  }

  override fun Queryable.getPlaylistName(
    playlistId: PlaylistId,
    type: PlayListType
  ): PlaylistName = PlayListTable
    .select(PlayListTable.playListName)
    .where { (id eq playlistId.value) and (playListType eq type.id) }
    .sequence { it[playListName] }
    .single()
    .asPlaylistName

  override suspend fun getNext(playlistId: PlaylistId) = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { playListName greater SELECT_PLAYLIST_FROM_BIND_ID }
        .orderByAsc { playListName }
        .limit(1)
        .longForQuery { bindings -> bindings[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getPrevious(playlistId: PlaylistId) = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { playListName less SELECT_PLAYLIST_FROM_BIND_ID }
        .orderBy { playListName by Order.DESC }
        .limit(1)
        .longForQuery { bindings -> bindings[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  private val playlistMin by lazy { PlayListTable.playListName.min().alias("playlist_min_alias") }
  override suspend fun getMin(): Result<PlaylistId, DaoExceptionMessage> = runSuspendCatching {
    db.query {
      PlayListTable
        .selects { listOf(id, playlistMin) }
        .all()
        .limit(1)
        .sequence { cursor -> PlaylistId(cursor[id]) }
        .single()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  private val playlistMax by lazy { PlayListTable.playListName.max().alias("playlist_max_alias") }
  override suspend fun getMax() = runSuspendCatching {
    db.query {
      PlayListTable
        .selects { listOf(id, playlistMax) }
        .all()
        .limit(1)
        .sequence { cursor -> PlaylistId(cursor[id]) }
        .single()
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

  override suspend fun getRandom(): Result<PlaylistId, DaoMessage> = runSuspendCatching {
    db.query {
      PlayListTable
        .select(PlayListTable.id)
        .where { id inSubQuery PlayListTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asPlaylistId
    }
  }.mapError { cause -> DaoExceptionMessage(cause) }

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
    PlayListTable
      .updateColumns { it[updatedTime] = System.currentTimeMillis() }
      .where { id eq playlistId.value }
      .update()
    return count
  }

  private fun Queryable.getNextSortValue(id: PlaylistId): Long {
    val max = PlayListMediaTable
      .select(PlayListMediaTable.sort.max())
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

data class SmartPlaylistDescription(
  val id: PlaylistId,
  val name: PlaylistName,
  val viewName: String
)

