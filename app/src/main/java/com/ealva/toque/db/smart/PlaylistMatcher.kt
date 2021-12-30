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

package com.ealva.toque.db.smart

import com.ealva.toque.R
import com.ealva.toque.common.fetch
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.PlayListMediaTable
import com.ealva.toque.db.PlayListType
import com.ealva.toque.ui.library.smart.asPlaylistMatcherData
import com.ealva.toque.ui.library.smart.playlistId
import com.ealva.toque.ui.library.smart.playlistName
import com.ealva.toque.ui.library.smart.playlistType
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.isNotNull
import com.ealva.welite.db.expr.isNull
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.notInSubQuery
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.existingView
import kotlinx.parcelize.Parcelize

@Parcelize
enum class PlaylistMatcher(override val id: Int, private val stringRes: Int) : Matcher<Long> {
  Is(1, R.string.is_) {
    override fun doMakeWhereClause(
      column: Column<Long>,
      view: String,
      type: PlayListType,
      playlistId: Long
    ): Op<Boolean> = makeTheWhereClause(type, playlistId, view, true)

    override fun doMakeJoinTemplate(
      view: String,
      type: PlayListType,
      playlistId: Long
    ): JoinTemplate? = if (PlayListType.Rules == type) {
      RulesPlaylistJoinTemplate(view)
    } else {
      null
    }
  },
  IsNot(2, R.string.is_not) {
    override fun doMakeWhereClause(
      column: Column<Long>,
      view: String,
      type: PlayListType,
      playlistId: Long
    ): Op<Boolean> = makeTheWhereClause(type, playlistId, view, false)

    override fun doMakeJoinTemplate(
      view: String,
      type: PlayListType,
      playlistId: Long
    ): JoinTemplate? = if (PlayListType.Rules == type) {
      RulesPlaylistJoinTemplate(view)
    } else {
      null
    }
  };

  override fun willAccept(data: MatcherData): Boolean =
    data.playlistType != null && data.playlistId > 0 && data.playlistName.value.isNotBlank()

  /**
   * Make a where clause where PlayListType is [data].first, view name is [data].text, and playlist
   * ID is [data].second
   */
  override fun makeWhereClause(column: Column<Long>, data: MatcherData): Op<Boolean> {
    val playlistData = data.asPlaylistMatcherData
    return doMakeWhereClause(
      column,
      playlistData.name.value,
      playlistData.type,
      playlistData.id.value
    )
  }

  protected abstract fun doMakeWhereClause(
    column: Column<Long>,
    view: String,
    type: PlayListType,
    playlistId: Long
  ): Op<Boolean>

  fun makeJoinTemplate(data: MatcherData): JoinTemplate? {
    val playlistData = data.asPlaylistMatcherData
    return doMakeJoinTemplate(
      playlistData.name.value,
      playlistData.type,
      playlistData.id.value
    )
  }

  protected abstract fun doMakeJoinTemplate(
    view: String,
    type: PlayListType,
    playlistId: Long
  ): JoinTemplate?

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES: List<PlaylistMatcher> = values().toList()

    fun fromId(matcherId: Int): PlaylistMatcher {
      return ALL_VALUES.find { it.id == matcherId }
        ?: throw IllegalArgumentException("No matcher with id=$matcherId")
    }

    private fun makeTheWhereClause(
      type: PlayListType,
      playlistId: Long,
      view: String,
      inList: Boolean
    ): Op<Boolean> {
      return when (type) {
        PlayListType.File, PlayListType.UserCreated -> makeListWhereClause(inList, playlistId)
        PlayListType.Rules -> makeRulesWhereClause(inList, view)
        else -> throw IllegalArgumentException("Playlist type must be File, UserCreated, or Rules")
      }
    }

    private fun makeListWhereClause(inList: Boolean, playlistId: Long) = if (inList) {
      MediaTable.id inSubQuery selectFromPlaylistMedia(playlistId)
    } else {
      MediaTable.id notInSubQuery selectFromPlaylistMedia(playlistId)
    }

    private fun selectFromPlaylistMedia(playlistId: Long) = PlayListMediaTable
      .select { mediaId }
      .where { playListId eq playlistId }

    private fun makeRulesWhereClause(inList: Boolean, viewName: String) = existingView(viewName)
      .column(MediaTable.id, MediaTable.id.name)
      .run { if (inList) isNotNull() else isNull() }
  }
}
