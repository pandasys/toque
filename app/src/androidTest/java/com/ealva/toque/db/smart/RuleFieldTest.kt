/*
 * Copyright 2022 Eric A. Snell
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
import com.ealva.toque.common.StarRating
import com.ealva.toque.db.AlbumTable
import com.ealva.toque.db.ComposerTable
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.smart.DateMatcher
import com.ealva.toque.db.smart.EmptySuggestionProvider
import com.ealva.toque.db.smart.GenreMatcher
import com.ealva.toque.db.smart.DurationMatcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.NumberMatcher
import com.ealva.toque.db.smart.PlaylistMatcher
import com.ealva.toque.db.smart.RatingMatcher
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.db.smart.SuggestionProvider
import com.ealva.toque.db.smart.SuggestionProviderFactory
import com.ealva.toque.db.smart.TextMatcher
import com.ealva.welite.db.table.Column
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RuleFieldTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testTitle() {
    val field = RuleField.Title
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, MediaTable.title)
    val matcher = TextMatcher.Is
    val data = MatcherData("TheTitle", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaTitle = 'TheTitle'""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testTitleBadMatcher() {
    RuleField.Title.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testAlbum() {
    val field = RuleField.Album
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, AlbumTable.albumTitle)
    val matcher = TextMatcher.Is
    val data = MatcherData("AlbumTitle", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Album.Album = 'AlbumTitle'""")
    expect(field.makeJoinClause(matcher, data)?.joinTo(MediaTable)?.toString())
      .toBe("""Media INNER JOIN Album ON Media.Media_AlbumId = Album.AlbumId""")
  }

  @Test(expected = NoSuchElementException::class)
  fun testAlbumBadMatcher() {
    RuleField.Album.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testArtist() {
    val field = RuleField.Artist
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, SmartPlaylist.songArtistName)
    val matcher = TextMatcher.Is
    val data = MatcherData("An Artist", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""SongArtist.Artist = 'An Artist'""")
    expect(field.makeJoinClause(matcher, data)?.joinTo(MediaTable)?.toString())
      .toBe(
        """Media INNER JOIN Artist AS SongArtist ON Media.Media_ArtistId""" +
          """ = SongArtist.ArtistId"""
      )
  }

  @Test(expected = NoSuchElementException::class)
  fun testArtistBadMatcher() {
    RuleField.Artist.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testAlbumArtist() {
    val field = RuleField.AlbumArtist
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, SmartPlaylist.albumArtistName)
    val matcher = TextMatcher.Is
    val data = MatcherData("Album Artist", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""AlbumArtist.Artist = 'Album Artist'""")
    expect(field.makeJoinClause(matcher, data)?.joinTo(MediaTable)?.toString())
      .toBe(
        """Media INNER JOIN Artist AS AlbumArtist ON Media.Media_ArtistId""" +
        """ = AlbumArtist.ArtistId"""
      )
  }

  @Test(expected = NoSuchElementException::class)
  fun testAlbumArtistBadMatcher() {
    RuleField.AlbumArtist.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testGenre() {
    val field = RuleField.Genre
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, MediaTable.genre)
    val matcher = GenreMatcher.Contains
    val data = MatcherData("Rock", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Genre.Genre LIKE '%Rock%' ESCAPE '\'""")
    expect(field.makeJoinClause(matcher, data)?.joinTo(MediaTable)?.toString())
      .toBe(
        """Media INNER JOIN GenreMedia ON Media.MediaId =""" +
          """ GenreMedia.GenreMedia_MediaId INNER JOIN Genre ON""" +
          """ GenreMedia.GenreMedia_GenreId = Genre.GenreId"""
      )
  }

  @Test(expected = NoSuchElementException::class)
  fun testGenreBadMatcher() {
    RuleField.Genre.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testComposer() {
    val field = RuleField.Composer
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProvider(field, ComposerTable.composer)
    val matcher = TextMatcher.Is
    val data = MatcherData("Bob Dylan", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Composer.Composer = 'Bob Dylan'""")
    expect(field.makeJoinClause(matcher, data)?.joinTo(MediaTable)?.toString())
      .toBe(
        """Media INNER JOIN ComposerMedia ON Media.MediaId =""" +
          """ ComposerMedia.ComposerMedia_MediaId INNER JOIN Composer ON""" +
          """ ComposerMedia.ComposerMedia_ComposerId = Composer.ComposerId"""
      )
  }

  @Test(expected = NoSuchElementException::class)
  fun testComposerBadMatcher() {
    RuleField.Composer.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testRating() {
    val field = RuleField.Rating
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = RatingMatcher.Is
    val data = MatcherData(StarRating.STAR_5)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaRating = 100""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testRatingBadMatcher() {
    RuleField.Rating.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testYear() {
    val field = RuleField.Year
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = NumberMatcher.Is
    val data = MatcherData("", 1970, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaYear = 1970""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testYearBadMatcher() {
    RuleField.Year.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testDateAdded() {
    val field = RuleField.DateAdded
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = DateMatcher.Is
    val data = MatcherData("", dateMillis, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaTimeCreated BETWEEN $dateMin AND $dateMax""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testDateAddedBadMatcher() {
    RuleField.Year.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testPlayCount() {
    val field = RuleField.PlayCount
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = NumberMatcher.Is
    val data = MatcherData("", 20, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaPlayedCount = 20""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testPlayCountBadMatcher() {
    RuleField.PlayCount.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testLastPlayed() {
    val field = RuleField.LastPlayed
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = DateMatcher.Is
    val data = MatcherData("", dateMillis, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaLastPlayedTime BETWEEN $dateMin AND $dateMax""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testLastPlayedBadMatcher() {
    RuleField.LastPlayed.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testSkipCount() {
    val field = RuleField.SkipCount
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = NumberMatcher.Is
    val data = MatcherData("", 5, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaSkippedCount = 5""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testSkipCountBadMatcher() {
    RuleField.SkipCount.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testLastSkipped() {
    val field = RuleField.LastSkipped
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = DateMatcher.Is
    val data = MatcherData("", dateMillis, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaLastSkippedTime BETWEEN $dateMin AND $dateMax""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testLastSkippedBadMatcher() {
    RuleField.LastSkipped.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }
  @ExperimentalTime
  @Test
  fun testDuration() {
    val field = RuleField.Duration
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = DurationMatcher.Is
    val first: Long = 777000
    val asMillis = 777.toDuration(DurationUnit.SECONDS).inWholeMilliseconds
    val low = asMillis - 500
    val high = asMillis + 499
    val data = MatcherData("", first, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaDuration BETWEEN $low AND $high""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testDurationBadMatcher() {
    RuleField.Duration.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }
  @Test
  fun testPlaylist() {
    val field = RuleField.Playlist
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = PlaylistMatcher.Is
    MatcherData("View Name", PlayListType.Rules.id.toLong(), 5L).let { data ->
      expect(field.makeWhereClause(matcher, data).toString())
        .toBe(""""View Name".MediaId IS NOT NULL""")
      val joinTemplate = field.makeJoinClause(matcher, data)
      expect(joinTemplate?.joinTo(MediaTable)?.toString())
        .toBe("""Media LEFT JOIN "View Name" ON Media.MediaId = "View Name".MediaId""")
    }

    MatcherData("", PlayListType.UserCreated.id.toLong(), 5).let { data ->
      expect(field.makeWhereClause(matcher, data).toString())
        .toBe(
          """Media.MediaId IN (SELECT PlayListMedia.PlayListMedia_MediaId FROM""" +
            """ PlayListMedia WHERE PlayListMedia.PlayListMedia_PlayListId = 5)"""
        )
      expect(field.makeJoinClause(matcher, data)?.toString()).toBeNull()
    }
  }

  @Test(expected = NoSuchElementException::class)
  fun testPlaylistBadMatcher() {
    RuleField.Playlist.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testComment() {
    val field = RuleField.Comment
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = TextMatcher.Is
    val data = MatcherData("Awesome song", 0, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaContentComment = 'Awesome song'""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testCommentBadMatcher() {
    RuleField.Comment.makeWhereClause(NumberMatcher.Is, MatcherData.EMPTY)
  }

  @Test
  fun testDiscCount() {
    val field = RuleField.DiscCount
    field.matchers.forEach { matcher ->
      expect(field.reifyMatcher(matcher.id)).toBe(matcher)
    }
    expectSuggestionProviderNotCalled(field)
    val matcher = NumberMatcher.Is
    val data = MatcherData("", 2, 0)
    expect(field.makeWhereClause(matcher, data).toString())
      .toBe("""Media.MediaTotalDiscs = 2""")
    expect(field.makeJoinClause(matcher, data)).toBeNull()
  }

  @Test(expected = NoSuchElementException::class)
  fun testDiscCountBadMatcher() {
    RuleField.DiscCount.makeWhereClause(TextMatcher.Is, MatcherData.EMPTY)
  }

  private fun expectSuggestionProvider(field: RuleField, column: Column<*>) {
    val factory = SuggestionProviderFactorySpy()
    val provider = field.getSuggestionsSource(factory)
    expect(factory._getProviderCalled).toBe(true)
    expect(provider).toBe(SuggestionProviderFake)
    expect(factory._getProviderColumn).toBeTheSameAs(column)
  }

  private fun expectSuggestionProviderNotCalled(field: RuleField) {
    val factory = SuggestionProviderFactorySpy()
    val provider = field.getSuggestionsSource(factory)
    expect(factory._getProviderCalled).toBe(false)
    expect(provider).toBe(EmptySuggestionProvider)
  }
}

@Suppress("PropertyName")
private class SuggestionProviderFactorySpy : SuggestionProviderFactory {
  var _getProviderCalled = false
  var _getProviderColumn : Column<*>? = null
  var _getProviderReturn: SuggestionProvider = SuggestionProviderFake
  override fun getProvider(column: Column<*>): SuggestionProvider {
    _getProviderCalled = true
    _getProviderColumn = column
    return _getProviderReturn
  }
}

private object SuggestionProviderFake : SuggestionProvider {
  override suspend fun getSuggestions(): List<String> = emptyList()
}

private val zonedDate = ZonedDateTime.of(1985, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)

// 487684800000 At noon on that day
private val dateMillis: Long = zonedDate
  .toInstant()
  .toEpochMilli()

private val dateMin = zonedDate
  .with(LocalTime.MIN)
  .toInstant()
  .toEpochMilli()

private val dateMax = zonedDate
  .with(LocalTime.MAX)
  .toInstant()
  .toEpochMilli()
