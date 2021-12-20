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

package com.ealva.toque.android.db.smart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.GenreTable
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.smart.AlbumArtistJoinTemplate
import com.ealva.toque.db.smart.AlbumJoinTemplate
import com.ealva.toque.db.smart.GenreJoinTemplate
import com.ealva.toque.db.smart.SmartOrderBy
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.db.smart.SongArtistJoinTemplate
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.table.OrderBy
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SmartOrderByTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testRandom() {
    expect(SmartOrderBy.Random.getOrderBy()).toBe(OrderBy.RANDOM)
    expect(SmartOrderBy.Random.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testTitle() {
    expect(SmartOrderBy.Title.getOrderBy()).toBe(OrderBy(MediaTable.titleSort))
    expect(SmartOrderBy.Title.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testAlbumArtist() {
    expect(SmartOrderBy.AlbumArtist.getOrderBy()).toBe(OrderBy(SmartPlaylist.albumArtistSort))
    expect(SmartOrderBy.AlbumArtist.makeJoinTemplate()).toBeTheSameAs(AlbumArtistJoinTemplate)
  }

  @Test
  fun testAlbum() {
    expect(SmartOrderBy.Album.getOrderBy()).toBe(OrderBy(AlbumTable.albumSort))
    expect(SmartOrderBy.Album.makeJoinTemplate()).toBeTheSameAs(AlbumJoinTemplate)
  }

  @Test
  fun testGenre() {
    expect(SmartOrderBy.Genre.getOrderBy()).toBe(OrderBy(GenreTable.genre))
    expect(SmartOrderBy.Genre.makeJoinTemplate()).toBeTheSameAs(GenreJoinTemplate)
  }

  @Test
  fun testArtist() {
    expect(SmartOrderBy.Artist.getOrderBy()).toBe(OrderBy(SmartPlaylist.songArtistSort))
    expect(SmartOrderBy.Artist.makeJoinTemplate()).toBeTheSameAs(SongArtistJoinTemplate)
  }

  @Test
  fun testHighestRating() {
    expect(SmartOrderBy.HighestRating.getOrderBy()).toBe(OrderBy(MediaTable.rating, Order.DESC))
    expect(SmartOrderBy.HighestRating.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testLowestRating() {
    expect(SmartOrderBy.LowestRating.getOrderBy()).toBe(OrderBy(MediaTable.rating, Order.ASC))
    expect(SmartOrderBy.LowestRating.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testMostOftenPlayed() {
    expect(SmartOrderBy.MostOftenPlayed.getOrderBy())
      .toBe(OrderBy(MediaTable.playedCount, Order.DESC))
    expect(SmartOrderBy.MostOftenPlayed.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testLeastOftenPlayed() {
    expect(SmartOrderBy.LeastOftenPlayed.getOrderBy())
      .toBe(OrderBy(MediaTable.playedCount, Order.ASC))
    expect(SmartOrderBy.LeastOftenPlayed.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testMostRecentlyAdded() {
    expect(SmartOrderBy.MostRecentlyAdded.getOrderBy())
      .toBe(OrderBy(MediaTable.createdTime, Order.DESC))
    expect(SmartOrderBy.MostRecentlyAdded.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testLeastRecentlyAdded() {
    expect(SmartOrderBy.LeastRecentlyAdded.getOrderBy())
      .toBe(OrderBy(MediaTable.createdTime, Order.ASC))
    expect(SmartOrderBy.LeastRecentlyAdded.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testMostRecentlyPlayed() {
    expect(SmartOrderBy.MostRecentlyPlayed.getOrderBy())
      .toBe(OrderBy(MediaTable.lastPlayedTime, Order.DESC))
    expect(SmartOrderBy.MostRecentlyPlayed.makeJoinTemplate()).toBeNull()
  }

  @Test
  fun testLeastRecentlyPlayed() {
    expect(SmartOrderBy.LeastRecentlyPlayed.getOrderBy())
      .toBe(OrderBy(MediaTable.lastPlayedTime, Order.ASC))
    expect(SmartOrderBy.LeastRecentlyPlayed.makeJoinTemplate()).toBeNull()
  }
}
