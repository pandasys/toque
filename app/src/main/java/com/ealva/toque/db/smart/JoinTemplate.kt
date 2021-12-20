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

import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.ComposerMediaTable
import com.ealva.toque.db.ComposerTable
import com.ealva.toque.db.GenreMediaTable
import com.ealva.toque.db.GenreTable
import com.ealva.toque.db.MediaTable
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.view.ViewColumn
import com.ealva.welite.db.view.existingView

/**
 * A JoinTemplate makes a join against MediaTable, returning the join. If it's determined the join
 * is not needed (unclear why at this point as JoinTemplates are optional) it can return the
 * MediaTable.
 *
 * JoinTemplate implementations must implement equals/hashCode as they will be put in a set to
 * prevent duplicate joins. As it stands, only a SmartPlaylist matcher will need a join. For
 * organizational purposes, a SmartPlaylist is a View and the MediaTable will be joined against this
 * view to determine if a song is in a SmartPlaylist or not.
 *
 * Earlier versions of the schema would require joining multiple tables, but things like album and
 * artist have been denormalized to lessen the need for joins during SmartPlaylist processing.
 * Genres require subqueries and SmartPlaylist requires a join. When/if artists are parsed, they
 * will require subqueries for SmartPlaylist processing.
 */
interface JoinTemplate {
  fun joinTo(lhs: ColumnSet): Join
}

object AlbumJoinTemplate : JoinTemplate {
  override fun joinTo(lhs: ColumnSet): Join =
    lhs.join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
}

object AlbumArtistJoinTemplate : JoinTemplate {
  override fun joinTo(lhs: ColumnSet): Join = lhs.join(
    SmartPlaylist.AlbumArtistTable,
    JoinType.INNER,
    MediaTable.artistId,
    SmartPlaylist.albumArtistId
  )
}

object SongArtistJoinTemplate : JoinTemplate {
  override fun joinTo(lhs: ColumnSet): Join = lhs.join(
    SmartPlaylist.SongArtistTable,
    JoinType.INNER,
    MediaTable.artistId,
    SmartPlaylist.songArtistId
  )
}

object GenreJoinTemplate : JoinTemplate {
  override fun joinTo(lhs: ColumnSet): Join = lhs
    .join(GenreMediaTable, JoinType.INNER, MediaTable.id, GenreMediaTable.mediaId)
    .join(GenreTable, JoinType.INNER, GenreMediaTable.genreId, GenreTable.id)
}

data class RulesPlaylistJoinTemplate(val viewName: String) : JoinTemplate {
  private val view = existingView(viewName)
  private val id: ViewColumn<Long> = view.column(MediaTable.id, MediaTable.id.name)

  override fun joinTo(lhs: ColumnSet): Join = lhs.join(
    view,
    JoinType.LEFT,
    MediaTable.id,
    id
  )
}

object ComposerJoinTemplate : JoinTemplate {
  override fun joinTo(lhs: ColumnSet): Join = lhs
    .join(ComposerMediaTable, JoinType.INNER, MediaTable.id, ComposerMediaTable.mediaId)
    .join(ComposerTable, JoinType.INNER, ComposerMediaTable.composerId, ComposerTable.id)
}
