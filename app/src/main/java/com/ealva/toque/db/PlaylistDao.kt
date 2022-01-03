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
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.common.getOrThrow
import com.ealva.toque.db.PlaylistDaoEvent.PlaylistCreated
import com.ealva.toque.db.PlaylistDaoEvent.PlaylistUpdated
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.db.smart.SmartPlaylistDao
import com.ealva.toque.db.smart.SmartPlaylistTable
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.ealva.toque.persist.asMediaId
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.persist.asPlaylistId
import com.ealva.toque.persist.reifyRequire
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.count
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Column
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
import com.ealva.welite.db.table.ordersBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.View
import com.ealva.welite.db.view.ViewColumn
import com.ealva.welite.db.view.existingView
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.orElseThrow
import com.github.michaelbull.result.toErrorIf
import com.github.michaelbull.result.unwrap
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private val LOG by lazyLogger(PlaylistDao::class)

data class PlaylistIdNameType(
  val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType
)

data class PlaylistDescription(
  val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType,
  val songCount: Long
)

data class PlaylistData(
  val id: PlaylistId,
  val name: PlaylistName,
  val type: PlayListType,
  val createdTime: Long,
  val updatedTime: Long,
  val sort: Int
)

sealed interface PlaylistDaoEvent {
  data class PlaylistCreated(val playlistId: PlaylistId) : PlaylistDaoEvent
  data class PlaylistUpdated(val playlistId: PlaylistId) : PlaylistDaoEvent
  data class PlaylistDeleted(val playlistId: PlaylistId) : PlaylistDaoEvent
}

interface PlaylistDao {
  val playlistDaoEvents: SharedFlow<PlaylistDaoEvent>

  /** Get all playlists [PlaylistIdNameType] whose type is in [types]. No [types] = all */
  suspend fun getAllOfType(vararg types: PlayListType): DaoResult<List<PlaylistIdNameType>>

  suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): DaoResult<PlaylistIdNameType>

  suspend fun createOrUpdateSmartPlaylist(smartPlaylist: SmartPlaylist): DaoResult<SmartPlaylist>

  suspend fun deletePlaylist(playlistId: PlaylistId): DaoResult<Memento>

  suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    limit: Limit = Limit.NoLimit
  ): DaoResult<MediaIdList>

  suspend fun getSmartPlaylist(playlistId: PlaylistId): DaoResult<SmartPlaylist>

  suspend fun addToUserPlaylist(id: PlaylistId, mediaIdList: MediaIdList): DaoResult<Long>

  suspend fun getAllPlaylists(): DaoResult<List<PlaylistDescription>>

  fun Queryable.getPlaylistName(playlistId: PlaylistId, type: PlayListType): PlaylistName

  suspend fun getNext(playlistId: PlaylistId): DaoResult<PlaylistId>
  suspend fun getPrevious(playlistId: PlaylistId): DaoResult<PlaylistId>
  suspend fun getMin(): DaoResult<PlaylistId>
  suspend fun getMax(): DaoResult<PlaylistId>
  suspend fun getRandom(): DaoResult<PlaylistId>

  suspend fun smartPlaylistsReferringTo(playlistId: PlaylistId): PlaylistIdList

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

  override suspend fun getAllOfType(
    vararg types: PlayListType
  ): DaoResult<List<PlaylistIdNameType>> = runSuspendCatching {
    db.query {
      PlayListTable.selects { listOf(id, playListName, playListType) }
        .where { types.toWhereClause(playListType) }
        .sequence { cursor ->
          PlaylistIdNameType(
            cursor[id].asPlaylistId,
            cursor[playListName].asPlaylistName,
            PlayListType::class.reifyRequire(cursor[playListType])
          )
        }
        .toList()
    }
  }

  override suspend fun createUserPlaylist(
    name: PlaylistName,
    mediaIdList: MediaIdList
  ): DaoResult<PlaylistIdNameType> = runSuspendCatching {
    db.transaction {
      val playlistId = PlayListTable.insert {
        it[playListName] = name.value
        it[playListType] = PlayListType.UserCreated.id
        it[createdTime] = Millis.currentUtcEpochMillis().value
        it[sort] = PlayListType.UserCreated.sortPosition
      }.asPlaylistId

      if (playlistId >= 1) {
        doAddMediaToPlaylist(mediaIdList, playlistId, 1)
      } else throw IllegalStateException("Could not create $name")
      PlaylistIdNameType(playlistId, name, PlayListType.UserCreated)
    }
  }.onSuccess { playlistIdName -> emit(PlaylistCreated(playlistId = playlistIdName.id)) }

  override suspend fun createOrUpdateSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ): DaoResult<SmartPlaylist> = runSuspendCatching {
    db.transaction {
      if (smartPlaylist.id.value < 1 || !playlistExists(smartPlaylist.id)) {
        with(smartPlaylistDao) { createSmartPlaylist(createRulesPlaylist(smartPlaylist)) }
      } else {
        updatePlaylist(smartPlaylist.id, smartPlaylist.name, Millis.currentUtcEpochMillis())
        with(smartPlaylistDao) { updateSmartPlaylist(smartPlaylist) }
      }
    }
  }.onSuccess { result ->
    emit(if (smartPlaylist.id < 0) PlaylistCreated(result.id) else PlaylistUpdated(result.id))
  }

  override suspend fun getMediaForPlaylists(
    playlistIds: PlaylistIdList,
    limit: Limit
  ): DaoResult<MediaIdList> = runSuspendCatching {
    db.query { mediaIdForPlaylists(playlistIds, limit) }
  }

  fun Queryable.mediaIdForPlaylists(
    playlistIds: PlaylistIdList,
    limit: Limit = Limit.NoLimit
  ) = PlayListMediaTable
    .join(PlayListTable, JoinType.INNER, PlayListMediaTable.playListId, PlayListTable.id)
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

  private fun TransactionInProgress.playlistExists(playlistId: PlaylistId): Boolean {
    return PlayListTable
      .select { id }
      .where { id eq playlistId.value }
      .longForQuery() > 0
  }

  override suspend fun getSmartPlaylist(playlistId: PlaylistId): DaoResult<SmartPlaylist> =
    smartPlaylistDao.getSmartPlaylist(playlistId)

  private fun TransactionInProgress.createRulesPlaylist(
    playlist: SmartPlaylist
  ): SmartPlaylist = playlist.copy(
    id = PlayListTable.insert {
      it[playListName] = playlist.name.value
      it[playListType] = PlayListType.Rules.id
      it[createdTime] = Millis.currentUtcEpochMillis().value
      it[sort] = PlayListType.Rules.sortPosition
    }.asPlaylistId
  )

  private fun TransactionInProgress.updatePlaylist(
    playlistId: PlaylistId,
    updateTime: Millis
  ): Long = PlayListTable
    .updateColumns { it[updatedTime] = updateTime.value }
    .where { id eq playlistId.value }
    .update()


  private fun TransactionInProgress.updatePlaylist(
    playlistId: PlaylistId,
    name: PlaylistName,
    updateTime: Millis
  ): Long = PlayListTable
    .updateColumns {
      it[playListName] = name.value
      it[updatedTime] = updateTime.value
    }
    .where { id eq playlistId.value }
    .update()

  override suspend fun addToUserPlaylist(
    id: PlaylistId,
    mediaIdList: MediaIdList
  ): DaoResult<Long> = runSuspendCatching {
    db.transaction {
      doAddMediaToPlaylist(mediaIdList, id, getNextSortValue(id))
      updatePlaylist(id, Millis.currentUtcEpochMillis())
    }
  }.onSuccess { emit(PlaylistUpdated(id)) }


  override suspend fun getAllPlaylists(): DaoResult<List<PlaylistDescription>> =
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
    }

  private fun Queryable.getSmartPlaylistSongCount(
    smart: SmartPlaylistDescription
  ): PlaylistDescription {
    val view = existingView(smart.viewName)
    val viewId = view.column(MediaTable.id, MediaTable.id.name)

    return PlaylistDescription(
      id = smart.id,
      name = smart.name,
      type = PlayListType.Rules,
      songCount = viewCount(view, viewId)
    )
  }

  private fun Queryable.viewCount(
    view: View,
    viewId: ViewColumn<Long>
  ): Long {
    return try {
      view
        .join(MediaTable, JoinType.INNER, viewId, MediaTable.id)
        .selectCount()
        .longForQuery()
    } catch (e: Exception) {
      LOG.e(e) { it("Error getting SmartPlaylist view count") }
      0L
    }
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

  override suspend fun getNext(playlistId: PlaylistId): DaoResult<PlaylistId> = runSuspendCatching {
    db.query {
      PlayListTable
        .select { id }
        .where { playListName greater SELECT_PLAYLIST_FROM_BIND_ID }
        .orderByAsc { playListName }
        .limit(1)
        .longForQuery { bindings -> bindings[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }

  override suspend fun getPrevious(
    playlistId: PlaylistId
  ): DaoResult<PlaylistId> = runSuspendCatching {
    db.query {
      PlayListTable
        .select { id }
        .where { playListName less SELECT_PLAYLIST_FROM_BIND_ID }
        .orderBy { playListName by Order.DESC }
        .limit(1)
        .longForQuery { bindings -> bindings[BIND_PLAYLIST_ID] = playlistId.value }
        .asPlaylistId
    }
  }

  private val playlistMin by lazy { PlayListTable.playListName.min().alias("playlist_min_alias") }
  override suspend fun getMin(): DaoResult<PlaylistId> = runSuspendCatching {
    db.query {
      PlayListTable
        .selects { listOf(id, playlistMin) }
        .all()
        .limit(1)
        .sequence { cursor -> PlaylistId(cursor[id]) }
        .single()
    }
  }

  private val playlistMax by lazy { PlayListTable.playListName.max().alias("playlist_max_alias") }
  override suspend fun getMax(): DaoResult<PlaylistId> = runSuspendCatching {
    db.query {
      PlayListTable
        .selects { listOf(id, playlistMax) }
        .all()
        .limit(1)
        .sequence { cursor -> PlaylistId(cursor[id]) }
        .single()
    }
  }

  override suspend fun getRandom(): DaoResult<PlaylistId> = runSuspendCatching {
    db.query {
      PlayListTable
        .select { id }
        .where { id inSubQuery PlayListTable.select(id).all().orderByRandom().limit(1) }
        .longForQuery()
        .asPlaylistId
    }
  }

  override suspend fun smartPlaylistsReferringTo(playlistId: PlaylistId): PlaylistIdList {
    return smartPlaylistDao.playlistsReferringTo(playlistId)
      .mapBoth(success = { it }, failure = { PlaylistIdList() })
  }

  private fun playlistDeleteUndo(playlistData: PlaylistData) {
    emit(PlaylistCreated(playlistData.id))
  }

  override suspend fun deletePlaylist(
    playlistId: PlaylistId
  ): DaoResult<Memento> = runSuspendCatching {
    db.transaction {
      PlayListTable
        .selectWhere { id eq playlistId.value }
        .sequence { cursor ->
          PlaylistData(
            id = cursor[id].asPlaylistId,
            name = cursor[playListName].asPlaylistName,
            type = PlayListType::class.reifyRequire(cursor[playListType]),
            createdTime = cursor[createdTime],
            updatedTime = cursor[updatedTime],
            sort = cursor[sort]
          )
        }
        .map { playlistData -> delete(playlistData, ::playlistDeleteUndo) }
        .single()
    }
  }.onSuccess { emit(PlaylistDaoEvent.PlaylistDeleted(playlistId)) }

  private fun TransactionInProgress.delete(
    playlistData: PlaylistData,
    onUndo: (PlaylistData) -> Unit
  ): Memento {
    return when (playlistData.type) {
      PlayListType.UserCreated -> deleteUserCreated(playlistData, onUndo)
      PlayListType.Rules -> deleteSmartPlaylist(playlistData, onUndo)
      PlayListType.File, PlayListType.System -> Memento.NullMemento
    }
  }

  private fun TransactionInProgress.deleteUserCreated(
    playlistData: PlaylistData,
    onUndo: (PlaylistData) -> Unit
  ): Memento = DeleteUserPlaylistMemento(
    playlistDao = this@PlaylistDaoImpl,
    playlistData = playlistData,
    mediaIdList = mediaIdForPlaylists(PlaylistIdList(playlistData.id)),
    referentRules = with(smartPlaylistDao) { deleteReferentRules(playlistData.id) }
      .mapError { cause ->
        IllegalStateException("Couldn't delete referent rules ${playlistData.name}", cause)
      }
      .orElseThrow()
      .unwrap(),
    onUndo = onUndo
  ).also {
    if (PlayListTable.delete { id eq playlistData.id.value } < 1)
      throw IllegalStateException("Could not delete ${playlistData.name} ${playlistData.id}")
  }

  @Suppress("ThrowableNotThrown")
  private fun TransactionInProgress.deleteSmartPlaylist(
    playlistData: PlaylistData,
    onUndo: (PlaylistData) -> Unit
  ): Memento {
    val playlist = with(smartPlaylistDao) {
      getPlaylist(playlistData.id)
        .mapError { cause -> IllegalStateException("Couldn't find $playlistData", cause) }
        .getOrThrow()
    }

    val referentRulesMemento = with(smartPlaylistDao) { deleteReferentRules(playlistData.id) }
      .mapError { cause ->
        IllegalStateException("Couldn't delete referent rules ${playlistData.name}", cause)
      }
      .getOrThrow()

    return DeleteSmartPlaylistMemento(
      playlistDao = this@PlaylistDaoImpl,
      playlistData = playlistData,
      playlist = playlist,
      referentRules = referentRulesMemento,
      onUndo = onUndo,
    ).also {
      check(PlayListTable.delete { id eq playlistData.id.value } == 1L)
      playlist.asView().drop()
    }
  }

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
      .updateColumns { it[updatedTime] = Millis.currentUtcEpochMillis().value }
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

  suspend fun restoreDeletedPlaylist(
    playlistData: PlaylistData
  ): DaoResult<Long> = runSuspendCatching {
    db.transaction {
      PlayListTable.insert {
        it[id] = playlistData.id.value
        it[playListName] = playlistData.name.value
        it[playListType] = playlistData.type.id
        it[createdTime] = playlistData.createdTime
        it[updatedTime] = playlistData.updatedTime
        it[sort] = playlistData.sort
      }
    }
  }.toErrorIf({ it < 1 }) { DaoException("Could not restore ${playlistData.name}") }

  suspend fun createSmartPlaylist(
    playlist: SmartPlaylist
  ): DaoResult<SmartPlaylist> = runSuspendCatching {
    db.transaction { with(smartPlaylistDao) { createSmartPlaylist(playlist) } }
  }
}

private class DeleteSmartPlaylistMemento(
  private val playlistDao: PlaylistDaoImpl,
  private val playlistData: PlaylistData,
  private val playlist: SmartPlaylist,
  private val referentRules: Memento,
  private val onUndo: (PlaylistData) -> Unit
) : BaseMemento() {
  override suspend fun doUndo() {
    playlistDao.restoreDeletedPlaylist(playlistData)
      .andThen { playlistDao.createSmartPlaylist(playlist) }
      .onFailure { msg -> LOG.e { it("Could not undo delete playlist. %s", msg) } }
      .onSuccess { referentRules.undo() }
      .onSuccess { onUndo(playlistData) }
  }

}

private class DeleteUserPlaylistMemento(
  private val playlistDao: PlaylistDaoImpl,
  private val playlistData: PlaylistData,
  private val mediaIdList: MediaIdList,
  private val referentRules: Memento,
  private val onUndo: (PlaylistData) -> Unit
) : BaseMemento() {
  override suspend fun doUndo() {
    playlistDao.restoreDeletedPlaylist(playlistData)
      .andThen { playlistDao.addToUserPlaylist(playlistData.id, mediaIdList) }
      .onFailure { cause -> LOG.e(cause) { it("Could not undo delete %s", playlistData) } }
      .onSuccess { referentRules.undo() }
      .onSuccess { onUndo(playlistData) }
  }
}

private fun Array<out PlayListType>.toWhereClause(playListType: Column<Int>): Op<Boolean>? {
  return if (isEmpty()) null else asSequence()
    .map { type -> playListType eq type.id }
    .reduce { acc, op -> acc and op }
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

