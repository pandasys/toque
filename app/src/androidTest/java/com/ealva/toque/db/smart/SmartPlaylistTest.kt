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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.Limit
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.db.smart.AnyOrAll
import com.ealva.toque.db.smart.EndOfSmartPlaylistAction
import com.ealva.toque.db.smart.GenreMatcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.Rule
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.SmartOrderBy
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.db.smart.TextMatcher
import com.ealva.toque.persist.PlaylistId
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SmartPlaylistTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testBuildQueryContainsAlbumContainsAlbumArtist() {
    val rules = listOf(
      Rule(1, RuleField.Album, TextMatcher.Contains, MatcherData("Blue", 0, 0)),
      Rule(2, RuleField.AlbumArtist, TextMatcher.Contains, MatcherData("Dylan", 0, 0))
    )

    val query = SmartPlaylist(
      PlaylistId.INVALID,
      "name".asPlaylistName,
      AnyOrAll.All,
      Limit.NoLimit,
      SmartOrderBy.None,
      EndOfSmartPlaylistAction.Reshuffle,
      rules,
    ).asQuery()

    expect(query.toString()).toBe(
      """SELECT Media.MediaId FROM Media INNER JOIN Album ON Media.Media_AlbumId""" +
        """ = Album.AlbumId INNER JOIN Artist AS AlbumArtist ON Media.Media_ArtistId""" +
        """ = AlbumArtist.ArtistId WHERE Media.MediaType = 1 AND Album.Album LIKE""" +
        """ '%Blue%' ESCAPE '\' AND AlbumArtist.Artist LIKE '%Dylan%' ESCAPE '\' GROUP BY""" +
        """ Media.MediaId"""
    )
  }

  @Test
  fun testBuildQueryIsGenreContainsComposer() {
    val rules = listOf(
      Rule(1, RuleField.Genre, GenreMatcher.Is, MatcherData("Rock", 0, 0)),
      Rule(2, RuleField.Composer, TextMatcher.Contains, MatcherData("Dylan", 0, 0))
    )
    val query = SmartPlaylist(
      PlaylistId.INVALID,
      "none".asPlaylistName,
      AnyOrAll.All,
      Limit.NoLimit,
      SmartOrderBy.Album,
      EndOfSmartPlaylistAction.Replay,
      rules
    ).asQuery()
    expect(query.toString()).toBe(
      """SELECT Media.MediaId FROM Media INNER JOIN GenreMedia ON Media.MediaId =""" +
        """ GenreMedia.GenreMedia_MediaId INNER JOIN Genre ON""" +
        """ GenreMedia.GenreMedia_GenreId = Genre.GenreId INNER JOIN""" +
        """ ComposerMedia ON Media.MediaId = ComposerMedia.ComposerMedia_MediaId""" +
        """ INNER JOIN Composer ON ComposerMedia.ComposerMedia_ComposerId =""" +
        """ Composer.ComposerId INNER JOIN Album ON Media.Media_AlbumId =""" +
        """ Album.AlbumId WHERE Media.MediaType = 1 AND Genre.Genre = 'Rock' AND""" +
        """ Composer.Composer LIKE '%Dylan%' ESCAPE '\' GROUP BY Media.MediaId ORDER BY""" +
        """ Album.AlbumSort"""
    )
  }

  @Test
  fun testBuildQueryIsGenreOrIsGenre() {
    val rules = listOf(
      Rule(1, RuleField.Genre, GenreMatcher.Is, MatcherData("Rock", 0, 0)),
      Rule(2, RuleField.Genre, GenreMatcher.Is, MatcherData("Alternative", 0, 0))
    )
    val query = SmartPlaylist(
      PlaylistId.INVALID,
      "none".asPlaylistName,
      AnyOrAll.Any,
      Limit.NoLimit,
      SmartOrderBy.Album,
      EndOfSmartPlaylistAction.Replay,
      rules
    ).asQuery()
    expect(query.toString()).toBe(
      """SELECT Media.MediaId FROM Media INNER JOIN GenreMedia ON Media.MediaId""" +
        """ = GenreMedia.GenreMedia_MediaId INNER JOIN Genre ON""" +
        """ GenreMedia.GenreMedia_GenreId = Genre.GenreId INNER JOIN Album ON""" +
        """ Media.Media_AlbumId = Album.AlbumId WHERE Media.MediaType = 1 AND""" +
        """ (Genre.Genre = 'Rock' OR Genre.Genre = 'Alternative') GROUP BY""" +
        """ Media.MediaId ORDER BY Album.AlbumSort"""
    )
  }
}
