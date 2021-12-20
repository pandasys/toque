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
import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.GenreTable
import com.ealva.toque.db.MediaTable
import com.ealva.toque.persist.HasConstId
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.table.OrderBy

private const val ID_NONE = 65536

enum class SmartOrderBy(override val id: Int, private val stringRes: Int) : HasConstId {
  Random(1, R.string.Random) {
    override fun getOrderBy(): OrderBy = OrderBy.RANDOM
  },
  Title(2, R.string.Title) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.titleSort)
  },
  AlbumArtist(3, R.string.Album_artist) {
    override fun getOrderBy(): OrderBy = OrderBy(SmartPlaylist.albumArtistSort)

    override fun makeJoinTemplate(): JoinTemplate = AlbumArtistJoinTemplate
  },
  Album(4, R.string.Album) {
    override fun getOrderBy(): OrderBy = OrderBy(AlbumTable.albumSort)

    override fun makeJoinTemplate(): JoinTemplate = AlbumJoinTemplate
  },
  Genre(5, R.string.Genre) {
    override fun getOrderBy(): OrderBy = OrderBy(GenreTable.genre)

    override fun makeJoinTemplate(): JoinTemplate = GenreJoinTemplate
  },
  Artist(6, R.string.Artist) {
    override fun getOrderBy(): OrderBy = OrderBy(SmartPlaylist.songArtistSort)

    override fun makeJoinTemplate(): JoinTemplate = SongArtistJoinTemplate
  },
  HighestRating(7, R.string.HighestRating) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.rating, Order.DESC)
  },
  LowestRating(8, R.string.LowestRating) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.rating, Order.ASC)
  },
  MostOftenPlayed(9, R.string.MostOftenPlayed) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.playedCount, Order.DESC)
  },
  LeastOftenPlayed(10, R.string.LeastOftenPlayed) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.playedCount, Order.ASC)
  },
  MostRecentlyAdded(11, R.string.MostRecentlyAdded) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.createdTime, Order.DESC)
  },
  LeastRecentlyAdded(12, R.string.LeastRecentlyAdded) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.createdTime, Order.ASC)
  },
  MostRecentlyPlayed(13, R.string.MostRecentlyPlayed) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.lastPlayedTime, Order.DESC)
  },
  LeastRecentlyPlayed(14, R.string.LeastRecentlyPlayed) {
    override fun getOrderBy(): OrderBy = OrderBy(MediaTable.lastPlayedTime, Order.ASC)
  },
  None(ID_NONE, R.string.None) {
    override fun getOrderBy(): OrderBy = OrderBy.NONE
  };

  abstract fun getOrderBy(): OrderBy

  open fun makeJoinTemplate(): JoinTemplate? = null

  override fun toString(): String {
    return fetch(stringRes)
  }

  companion object {
    val allValues: List<SmartOrderBy> = values().toList()
  }
}
