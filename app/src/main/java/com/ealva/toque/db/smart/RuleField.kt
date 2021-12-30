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
import com.ealva.toque.db.ComposerTable
import com.ealva.toque.db.MediaTable
import com.ealva.toque.db.PlayListTable
import com.ealva.toque.persist.HasConstId
import com.ealva.welite.db.expr.Op

enum class RuleField(
  override val id: Int,
  private val stringRes: Int,
) : HasConstId {
  Title(1, R.string.Title) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(MediaTable.title)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.title, data)
  },
  Album(2, R.string.Album) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(AlbumTable.albumTitle)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(AlbumTable.albumTitle, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate =
      AlbumJoinTemplate
  },
  Artist(3, R.string.Artist) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(SmartPlaylist.songArtistName)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(SmartPlaylist.songArtistName, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate =
      SongArtistJoinTemplate
  },
  AlbumArtist(4, R.string.Album_artist) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(SmartPlaylist.albumArtistName)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(SmartPlaylist.albumArtistName, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate =
      AlbumArtistJoinTemplate
  },
  Genre(5, R.string.Genre) {
    override val matchers: List<GenreMatcher> = GenreMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): GenreMatcher = GenreMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(MediaTable.genre)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.genre, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate =
      GenreJoinTemplate
  },
  Composer(6, R.string.Composer) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
      factory.getProvider(ComposerTable.composer)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(ComposerTable.composer, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate {
      return ComposerJoinTemplate
    }
  },
  Rating(7, R.string.Rating) {
    override val matchers: List<RatingMatcher> = RatingMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): RatingMatcher = RatingMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.rating, data)
  },
  Year(8, R.string.Year) {
    override val matchers: List<NumberMatcher> = NumberMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): NumberMatcher = NumberMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.year, data)
  },
  DateAdded(9, R.string.Date_added) {
    override val matchers: List<DateMatcher> = DateMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): DateMatcher = DateMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.createdTime, data)
  },
  PlayCount(10, R.string.Played_count) {
    override val matchers: List<NumberMatcher> = NumberMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): NumberMatcher = NumberMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.playedCount, data)
  },
  LastPlayed(11, R.string.Last_played) {
    override val matchers: List<DateMatcher> = DateMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): DateMatcher = DateMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.lastPlayedTime, data)
  },
  SkipCount(12, R.string.Skipped_count) {
    override val matchers: List<NumberMatcher> = NumberMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): NumberMatcher = NumberMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.skippedCount, data)
  },
  LastSkipped(13, R.string.Last_skipped) {
    override val matchers: List<DateMatcher> = DateMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): DateMatcher = DateMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.lastSkippedTime, data)
  },
  Duration(14, R.string.Duration) {
    override val matchers: List<DurationMatcher> = DurationMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): DurationMatcher =
      DurationMatcher.fromId(matcherId)

    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.duration, data)
  },
  Playlist(15, R.string.Playlist) {
    override val matchers: List<PlaylistMatcher> = PlaylistMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): PlaylistMatcher = PlaylistMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(PlayListTable.id, data)

    override fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate? =
      matchers.first { it === matcher }.makeJoinTemplate(data)
  },
  Comment(16, R.string.Comment) {
    override val matchers: List<TextMatcher> = TextMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): TextMatcher = TextMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.comment, data)
  },
  DiscCount(17, R.string.Disc_count) {
    override val matchers: List<NumberMatcher> = NumberMatcher.ALL_VALUES
    override fun reifyMatcher(matcherId: Int): NumberMatcher = NumberMatcher.fromId(matcherId)
    override fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean> =
      matchers.first { it === matcher }.makeWhereClause(MediaTable.totalDiscs, data)
  };

  abstract val matchers: List<Matcher<*>>

  abstract fun reifyMatcher(matcherId: Int): Matcher<*>

  fun contains(matcher: Matcher<*>): Boolean = matchers.contains(matcher)

  @Suppress("unused")
  fun doesNotContain(matcher: Matcher<*>): Boolean = !matchers.contains(matcher)

  /**
   * Given the [matcher] and [data], make the part of the "where" expression for this RuleField.
   * If the matcher is not from this RuleField a [NoSuchElementException] exception is thrown
   */
  abstract fun makeWhereClause(matcher: Matcher<*>, data: MatcherData): Op<Boolean>

  /**
   * Given the [matcher] and [data], make any [JoinTemplate] necessary for this RuleField.
   */
  open fun makeJoinClause(matcher: Matcher<*>, data: MatcherData): JoinTemplate? = null

  open fun getSuggestionsSource(factory: SuggestionProviderFactory): SuggestionProvider =
    EmptySuggestionProvider

  override fun toString(): String = fetch(stringRes)

  companion object {
    val ALL_VALUES = values().asList()
  }
}
