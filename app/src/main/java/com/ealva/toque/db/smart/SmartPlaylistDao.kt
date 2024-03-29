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

@file:Suppress("RemoveRedundantQualifierName")

package com.ealva.toque.db.smart

import com.ealva.toque.common.Limit
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.db.BaseMemento
import com.ealva.toque.db.DaoResult
import com.ealva.toque.db.Memento
import com.ealva.toque.db.PlayListTable
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.ealva.toque.persist.isValid
import com.ealva.toque.persist.reifyRequire
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.bindInt
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching

interface SmartPlaylistDao {
  suspend fun getSmartPlaylist(playlistId: PlaylistId): DaoResult<SmartPlaylist>

  fun Queryable.getPlaylist(playlistId: PlaylistId): Result<SmartPlaylist, Throwable>

  fun TransactionInProgress.createSmartPlaylist(smartPlaylist: SmartPlaylist): SmartPlaylist
  fun TransactionInProgress.updateSmartPlaylist(smartPlaylist: SmartPlaylist): SmartPlaylist

  fun TransactionInProgress.deleteReferentRules(id: PlaylistId): Result<Memento, Throwable>
  suspend fun playlistsReferringTo(playlistId: PlaylistId): Result<PlaylistIdList, Throwable>

  companion object {
    operator fun invoke(db: Database): SmartPlaylistDao = SmartPlaylistDaoImpl(db)
  }
}

private class SmartPlaylistDaoImpl(private val db: Database) : SmartPlaylistDao {
  override suspend fun getSmartPlaylist(
    playlistId: PlaylistId
  ): DaoResult<SmartPlaylist> = runSuspendCatching {
    db.query { playlist(playlistId) }
  }

  private fun Queryable.playlist(playlistId: PlaylistId): SmartPlaylist = SmartPlaylistTable
    .selectWhere { smartId eq playlistId.value }
    .sequence { cursor ->
      SmartPlaylist(
        id = playlistId,
        name = cursor[smartName].asPlaylistName,
        anyOrAll = AnyOrAll::class.reifyRequire(cursor[smartAnyOrAll]),
        limit = Limit(cursor[smartLimit]),
        selectBy = SmartOrderBy::class.reifyRequire(cursor[smartOrderBy]),
        endOfListAction = EndOfSmartPlaylistAction::class
          .reifyRequire(cursor[smartEndOfListAction]),
        ruleList = emptyList()
      )
    }.map { playlist -> playlist.copy(ruleList = getPlaylistRules(playlistId)) }
    .single()

  override fun Queryable.getPlaylist(
    playlistId: PlaylistId
  ): Result<SmartPlaylist, Throwable> = runSuspendCatching {
    playlist(playlistId)
  }

  private fun Queryable.getPlaylistRules(playlistId: PlaylistId): List<Rule> =
    SmartPlaylistRuleTable
      .selectWhere { smartPlaylistId eq playlistId.value }
      .sequence {
        Rule(
          id = it[id],
          ruleField = RuleField::class.reifyRequire(it[field]),
          matcherId = it[matcher],
          data = MatcherData(
            text = it[matcherText],
            first = it[matcherFirst],
            second = it[matcherSecond]
          )
        )
      }
      .toList()

  override fun TransactionInProgress.createSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ): SmartPlaylist = smartPlaylist.apply {
    require(smartPlaylist.id.isValid) { "Could not create playlist $smartPlaylist" }
    check(
      SmartPlaylistTable.insert {
        it[smartId] = smartPlaylist.id.value
        it[smartName] = name.value
        it[smartAnyOrAll] = anyOrAll.id
        it[smartLimit] = limit.value
        it[smartOrderBy] = selectBy.id
        it[smartEndOfListAction] = endOfListAction.id
      } > 0) { "Could not create smart playlist $smartPlaylist" }
    replacePlaylistRules(id, ruleList)
    createOrUpdateView(this)
  }

  private fun TransactionInProgress.replacePlaylistRules(id: PlaylistId, ruleList: List<Rule>) {
    SmartPlaylistRuleTable.delete { smartPlaylistId eq id.value }
    ruleList.forEach { rule ->
      val rowId = INSERT_RULE.insert {
        it[BIND_SMART_PLAYLIST_ID] = id.value
        it[BIND_RULE_FIELD] = rule.ruleField.id
        it[BIND_MATCHER] = rule.matcher.id
        it[BIND_MATCHER_TEXT] = rule.data.text
        it[BIND_MATCHER_FIRST] = rule.data.first
        it[BIND_MATCHERS_SECOND] = rule.data.second
      }
      check(rowId > 0) { "Could not create smart playlist rule $rule" }
    }
  }

  override fun TransactionInProgress.updateSmartPlaylist(
    smartPlaylist: SmartPlaylist
  ) = smartPlaylist.apply {
    val oldPlaylist = playlist(smartPlaylist.id).apply {
      if (isValid) asView().drop()
    }
    val updated = SmartPlaylistTable
      .updateColumns {
        it[smartName] = name.value
        it[smartAnyOrAll] = anyOrAll.id
        it[smartLimit] = limit.value
        it[smartOrderBy] = selectBy.id
        it[smartEndOfListAction] = endOfListAction.id
      }
      .where { smartId eq smartPlaylist.id.value }
      .update()

    check(updated > 0) { "Could not update smart playlist $smartPlaylist" }
    replacePlaylistRules(smartPlaylist.id, smartPlaylist.ruleList)
    createOrUpdateView(smartPlaylist)
    updateReferentRules(oldPlaylist, smartPlaylist)
  }

  data class SmartPlaylistRuleEntry(
    val id: Long,
    val smartPlaylistId: Long,
    val field: Int,
    val matcher: Int,
    val matcherText: String,
    val matcherFirst: Long,
    val matcherSecond: Long
  )

  override fun TransactionInProgress.deleteReferentRules(
    id: PlaylistId
  ): Result<Memento, Throwable> = runSuspendCatching {
    SmartPlaylistRulesMemento(
      this@SmartPlaylistDaoImpl,
      getEntryListFor(id)
    ).also {
      SmartPlaylistRuleTable
        .delete { (field eq RuleField.Playlist.id) and (matcherSecond eq id.value) }
    }
  }

  override suspend fun playlistsReferringTo(
    playlistId: PlaylistId
  ): Result<PlaylistIdList, Throwable> = runSuspendCatching {
    db.query {
      PlaylistIdList().apply {
        SmartPlaylistRuleTable
          .select { smartPlaylistId }
          .where { (field eq RuleField.Playlist.id) and (matcherSecond eq playlistId.value) }
          .distinct()
          .sequence { add(PlaylistId(it[smartPlaylistId])) }
      }
    }
  }

  private fun Queryable.getEntryListFor(
    playlistId: PlaylistId
  ): List<SmartPlaylistRuleEntry> = SmartPlaylistRuleTable
    .selectWhere { (field eq RuleField.Playlist.id) and (matcherSecond eq playlistId.value) }
    .sequence { cursor ->
      SmartPlaylistRuleEntry(
        id = cursor[id],
        smartPlaylistId = cursor[smartPlaylistId],
        field = cursor[field],
        matcher = cursor[matcher],
        matcherText = cursor[matcherText],
        matcherFirst = cursor[matcherFirst],
        matcherSecond = cursor[matcherSecond]
      )
    }
    .toList()

  class SmartPlaylistRulesMemento(
    private val smartPlaylistDao: SmartPlaylistDaoImpl,
    private val entryList: List<SmartPlaylistRuleEntry>
  ) : BaseMemento() {
    override suspend fun doUndo() {
      smartPlaylistDao.restoreEntries(entryList)
    }
  }

  private suspend fun restoreEntries(entryList: List<SmartPlaylistRuleEntry>) {
    if (entryList.isNotEmpty()) {
      db.transaction {
        entryList.forEach { entry ->
          SmartPlaylistRuleTable
            .insert {
              it[id] = entry.id
              it[smartPlaylistId] = entry.smartPlaylistId
              it[field] = entry.field
              it[matcher] = entry.matcher
              it[matcherText] = entry.matcherText
              it[matcherFirst] = entry.matcherFirst
              it[matcherSecond] = entry.matcherSecond
            }
        }
      }
    }
  }

  private fun TransactionInProgress.createOrUpdateView(smartPlaylist: SmartPlaylist) {
    smartPlaylist.asView().let { view ->
      view.drop()
      view.create()
    }
  }

  /**
   * This function finds all rule data which refers to [oldPlaylist] and updates them to refer to
   * [smartPlaylist]
   */
  private fun TransactionInProgress.updateReferentRules(
    oldPlaylist: SmartPlaylist,
    smartPlaylist: SmartPlaylist
  ) {
    if (oldPlaylist.id == smartPlaylist.id && oldPlaylist.name != smartPlaylist.name) {
      SmartPlaylistRuleTable
        .updateColumns { it[matcherText] = smartPlaylist.name.value }
        .where { (field eq RuleField.Playlist.id) and (matcherSecond eq smartPlaylist.id.value) }
        .update()
    }
  }
}

/**
 * All the data, save for the rule list, of a SmartPlaylist. The [SmartPlaylistRuleTable] contains
 * all the Rules for the table
 */
object SmartPlaylistTable : Table() {
  val id = long("SmartPlaylist_id") { primaryKey() }

  /** Display name the user chose */
  val smartName = text("SmartName") { collateNoCase() }

  /** The ID of this SmartPlaylist in the PlaylistTable */
  val smartId = reference("SmartPlaylistId", PlayListTable.id, onDelete = ForeignKeyAction.CASCADE)

  /** An [AnyOrAll] ID */
  val smartAnyOrAll = integer("SmartAnyOrAll") { default(AnyOrAll.All.id) }

  /** Any limit to the results of a select of this SmartPlaylist */
  val smartLimit = long("SmartLimit") { default(Limit.NoLimit.value) }

  /** How results should be ordered in this SmartPlaylist */
  val smartOrderBy = integer("SmartOrderBy") { default(SmartOrderBy.None.id) }

  /**
   * The [EndOfSmartPlaylistAction] ID representing what should happen when the up next queue
   * reaches the end of playing this list. We treat this specially so the user can create a
   * dynamic playlist that replays or reshuffles itself into the queue. The user can create a
   * radio station of sorts, choosing a mix of song ratings, least recently played, and other
   * combinations. The default is to do whatever the EndOfQueueAction which moves to another list
   * or stops.
   */
  val smartEndOfListAction = integer("SmartEndOfListAction") {
    default(EndOfSmartPlaylistAction.EndOfQueueAction.id)
  }

  init {
    uniqueIndex(smartName)
    uniqueIndex(smartId)
  }
}

/**
 * Data for a SmartPlaylist [Rule]. A SmartPlaylist can have unlimited rules. Typically there
 * aren't too many rules. Every rule generates a where clause and possibly a join. But
 * SmartPlaylists that refer to each other nested 3 and 4 deep have no performance problems,
 * even referring to thousands of media.
 */
object SmartPlaylistRuleTable : Table() {
  val id = long("SmartRule_id") { primaryKey() }
  val smartPlaylistId = reference(
    name = "SmartRulePlaylistId",
    refColumn = SmartPlaylistTable.smartId,
    onDelete = ForeignKeyAction.CASCADE
  )

  /** ID of the RuleField */
  val field = integer("SmartRuleField") { default(RuleField.Title.id) }

  /** Rule Matcher ID */
  val matcher = integer("SmartRuleMatcher") { default(TextMatcher.Is.id) }

  /**
   * The MatcherData text field. For most matchers this contains a name, such as a title, album
   * title, genre, artist, composer, etc. If referencing a SmartPlaylist, this contains the name
   * of the DB View created to represent the SmartPlaylist. This is for easier organization, the
   * view can be referenced instead of the entire Query (which can be unwieldy anyway)
   */
  val matcherText = text("SmartMatcherText") { collateNoCase().default("") }

  /**
   * The first long of the MatcherData. For playlist data, this column contains the
   * [com.ealva.toque.db.PlayListType]. For number, ratings, date... matchers, this contains the
   * value, or the first value of a range.
   */
  val matcherFirst = long("SmartMatcherFirst") { default(0) }

  /**
   * The second long of the MatcherData. For playlist matchers this contains the PlaylistId. For
   * number, rating, date... matchers, this contains the second value of a range.
   */
  val matcherSecond = long("SmartMatcherSecond") { default(0) }

  init {
    index(smartPlaylistId)
    index(field) // deleting rules referring to playlists
    index(matcherSecond) // deleting rules referring to playlists
  }
}

val BIND_SMART_PLAYLIST_ID = bindLong()
val BIND_RULE_FIELD = bindInt()
val BIND_MATCHER = bindInt()
val BIND_MATCHER_TEXT = bindString()
val BIND_MATCHER_FIRST = bindLong()
val BIND_MATCHERS_SECOND = bindLong()
private val INSERT_RULE = SmartPlaylistRuleTable.insertValues {
  it[SmartPlaylistRuleTable.smartPlaylistId] = BIND_SMART_PLAYLIST_ID
  it[SmartPlaylistRuleTable.field] = BIND_RULE_FIELD
  it[SmartPlaylistRuleTable.matcher] = BIND_MATCHER
  it[SmartPlaylistRuleTable.matcherText] = BIND_MATCHER_TEXT
  it[SmartPlaylistRuleTable.matcherFirst] = BIND_MATCHER_FIRST
  it[SmartPlaylistRuleTable.matcherSecond] = BIND_MATCHERS_SECOND
}
