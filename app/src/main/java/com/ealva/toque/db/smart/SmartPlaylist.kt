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

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.common.checkThen
import com.ealva.toque.db.ArtistTable
import com.ealva.toque.db.MediaTable
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.service.media.MediaType
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.toQuery
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.View
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class SmartPlaylist(
  val id: PlaylistId,
  val name: PlaylistName,
  val anyOrAll: AnyOrAll,
  val limit: Limit,
  val selectBy: SmartOrderBy,
  val endOfListAction: EndOfSmartPlaylistAction,
  val ruleList: List<Rule> = emptyList()
) : Parcelable {
  fun asView(): View = checkThen(isValid) {
    View(
      query = buildQuery(anyOrAll, ruleList, limit, selectBy),
      name = name.value,
      forceQuoteName = true
    )
  }

  fun asQuery(): Query<ColumnSet> = checkThen(isValid) {
    buildQuery(anyOrAll, ruleList, limit, selectBy)
  }

  val isValid: Boolean
    get() = name.isValid() && ruleList.isNotEmpty() && ruleList.all { rule -> rule.isValid }

  private fun PlaylistName.isValid() = value.isNotEmpty() && !value.startsWith("sqlite_", true)

  /**
   * There must be at least 1 rule for a SmartPlaylist
   */
  private fun buildQuery(
    anyOrAll: AnyOrAll,
    rules: List<Rule>,
    limit: Limit,
    selectBy: SmartOrderBy,
  ): Query<ColumnSet> = rules
    .mapNotNullTo(LinkedHashSet(rules.size)) { rule -> rule.makeJoinTemplate() }
    .apply { selectBy.makeJoinTemplate()?.let { template -> add(template) } }
    .fold(MediaTable as ColumnSet) { acc, joinTemplate -> joinTemplate.joinTo(acc) }
    .select { MediaTable.id }
    .where {
      (MediaTable.mediaType eq MediaType.Audio.id) and rules
        .asSequence()
        .map { rule -> rule.makeWhereClause() }
        .reduce { acc, op -> anyOrAll.apply(acc, op) }
    }
    .groupBy { MediaTable.id }
    .orderBy { selectBy.getOrderBy() }
    .limit(limit.value)
    .toQuery()

  companion object {
    val EMPTY = SmartPlaylist(
      PlaylistId.INVALID,
      "".asPlaylistName,
      AnyOrAll.All,
      Limit.NoLimit,
      SmartOrderBy.None,
      EndOfSmartPlaylistAction.EndOfQueueAction,
      listOf(
        Rule(1, RuleField.Title, RuleField.Title.matchers[0], MatcherData.EMPTY)
      )
    )

    val AlbumArtistTable = ArtistTable.alias("AlbumArtist")
    val SongArtistTable = ArtistTable.alias("SongArtist")

    val albumArtistId: Column<Long> = AlbumArtistTable[ArtistTable.id]
    val albumArtistName: Column<String> = AlbumArtistTable[ArtistTable.artistName]
    val albumArtistSort: Column<String> = AlbumArtistTable[ArtistTable.artistSort]
    val songArtistId: Column<Long> = SongArtistTable[ArtistTable.id]
    val songArtistName: Column<String> = SongArtistTable[ArtistTable.artistName]
    val songArtistSort: Column<String> = SongArtistTable[ArtistTable.artistSort]
  }
}
