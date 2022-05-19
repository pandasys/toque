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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.db.smart.SmartPlaylistRuleTable
import com.ealva.toque.db.smart.SmartPlaylistTable
import com.ealva.welite.db.Database
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.get

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DbModuleTest : KoinTest {
  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  @Test
  fun testExpectedTables() {
    val db: Database = get()
    val tables = db.tables
    expect(tables).toBe(
      listOf(
        ArtistTable,
        AlbumTable,
        ArtistAlbumTable,
        MediaTable,
        ArtistMediaTable,
        ComposerTable,
        ComposerMediaTable,
        GenreTable,
        GenreMediaTable,
        EqPresetTable,
        EqPresetAssociationTable,
        QueueTable,
        QueueItemsTable,
        QueueStateTable,
        PlayListTable,
        PlayListMediaTable,
        SmartPlaylistTable,
        SmartPlaylistRuleTable,
        SearchHistoryTable
      )
    )
  }

  @Test
  fun testExpectedDaos() {
    expect(get<ArtistDao>()).toNotBeNull()
    expect(get<AlbumDao>()).toNotBeNull()
    expect(get<ArtistAlbumDao>()).toNotBeNull()
    expect(get<ComposerDao>()).toNotBeNull()
    expect(get<GenreDao>()).toNotBeNull()
    expect(get<EqPresetDao>()).toNotBeNull()
    expect(get<EqPresetAssociationDao>()).toNotBeNull()
    expect(get<AudioMediaDao>()).toNotBeNull()
    expect(get<QueueDao>()).toNotBeNull()
    expect(get<QueuePositionStateDaoFactory>()).toNotBeNull()
    expect(get<PlaylistDao>()).toNotBeNull()
    expect(get<SearchDao>()).toNotBeNull()
  }
}
