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

package com.ealva.toque.ui.library.smart

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.asLong
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.ComposerDao
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.TextSearch
import com.ealva.toque.db.smart.GenreMatcher
import com.ealva.toque.db.smart.Matcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.NumberMatcher
import com.ealva.toque.db.smart.Rule
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.TextMatcher
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.PlaylistIdList
import com.ealva.toque.ui.library.smart.EditorRule.DateEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.DurationRule
import com.ealva.toque.ui.library.smart.EditorRule.NumberEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.PlaylistEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.TextEditorRule
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(EditorRule::class)

@Parcelize
enum class Capitalization : Parcelable {
  None {
    override val keyboardCapitalization: KeyboardCapitalization
      get() = KeyboardCapitalization.None
  },
  Characters {
    override val keyboardCapitalization: KeyboardCapitalization
      get() = KeyboardCapitalization.Characters
  },
  Words {
    override val keyboardCapitalization: KeyboardCapitalization
      get() = KeyboardCapitalization.Words
  },
  Sentences {
    override val keyboardCapitalization: KeyboardCapitalization
      get() = KeyboardCapitalization.Sentences
  };
  abstract val keyboardCapitalization: KeyboardCapitalization
}

@Immutable
sealed interface EditorRule : Parcelable {
  val rule: Rule

  /** Is the current [rule] considered "valid" - can be persisted without error */
  val isValid: Boolean get() = rule.isValid

  @Immutable
  @Parcelize
  data class TextEditorRule(
    override val rule: Rule,
    val editing: Boolean,
    val nameValidity: SmartPlaylistEditorViewModel.NameValidity,
    val suggestions: List<String>,
    val capitalization: Capitalization
  ) : EditorRule

  @Immutable
  @Parcelize
  data class RatingEditorRule(override val rule: Rule) : EditorRule

  @Immutable
  @Parcelize
  data class PlaylistEditorRule(
    override val rule: Rule,
    val index: Int,
    val playlistMatcherData: List<PlaylistMatcherData>
  ) : EditorRule

  @Immutable
  @Parcelize
  data class NumberEditorRule(
    override val rule: Rule,
    val firstText: String,
    val secondText: String,
    val firstLabel: String,
    val secondLabel: String,
    val firstIsError: Boolean,
    val secondIsError: Boolean
  ) : EditorRule

  @Immutable
  @Parcelize
  data class DateEditorRule(
    override val rule: Rule,
    val textValue: String
  ) : EditorRule

  @Immutable
  @Parcelize
  data class DurationRule(override val rule: Rule) : EditorRule

  companion object {
    suspend fun titleRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      audioMediaDao: AudioMediaDao
    ): EditorRule = makeTitleEditorRule(
      update = update as? TextEditorRule,
      ruleId = ruleId,
      matcher = matcher,
      data = data,
      audioMediaDao = audioMediaDao
    )

    suspend fun albumRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      albumDao: AlbumDao
    ): EditorRule = makeAlbumEditorRule(
      update = update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      albumDao
    )

    suspend fun artistRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      artistDao: ArtistDao
    ): EditorRule = makeArtistEditorRule(
      update = update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      artistDao
    )

    suspend fun albumArtistRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      artistDao: ArtistDao
    ): EditorRule = makeAlbumArtistEditorRule(
      update = update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      artistDao
    )

    suspend fun genreRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      genreDao: GenreDao
    ): EditorRule = makeGenreEditorRule(
      update = update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      genreDao
    )

    suspend fun composerRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      composerDao: ComposerDao
    ): EditorRule = makeComposerEditorRule(
      update = update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      composerDao
    )

    fun commentRule(ruleId: Long, matcher: Matcher<*>, data: MatcherData): EditorRule =
      makeCommentEditorRule(ruleId, matcher, data)

    fun ratingRule(ruleId: Long, matcher: Matcher<*>, data: MatcherData): EditorRule =
      RatingEditorRule(Rule(ruleId, RuleField.Rating, matcher, matcher.acceptableData(data)))

    suspend fun playlistRule(
      playlistId: PlaylistId,
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      playlistDao: PlaylistDao
    ): EditorRule = makePlaylistEditorRule(
      playlistId,
      update as? PlaylistEditorRule,
      ruleId,
      matcher,
      data,
      playlistDao
    )

    fun yearRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeNumberEditorRule(
      update as? NumberEditorRule,
      ruleId,
      RuleField.Year,
      matcher,
      data
    )

    fun playCountRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeNumberEditorRule(
      update as? NumberEditorRule,
      ruleId,
      RuleField.PlayCount,
      matcher,
      data
    )

    fun skipCountRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeNumberEditorRule(
      update as? NumberEditorRule,
      ruleId,
      RuleField.SkipCount,
      matcher,
      data
    )

    fun discCountRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeNumberEditorRule(
      update as? NumberEditorRule,
      ruleId,
      RuleField.DiscCount,
      matcher,
      data
    )

    fun dateAddedRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeDateEditorRule(update, ruleId, RuleField.DateAdded, matcher, data)

    fun lastPlayedRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeDateEditorRule(update, ruleId, RuleField.LastPlayed, matcher, data)

    fun lastSkippedRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeDateEditorRule(update, ruleId, RuleField.LastSkipped, matcher, data)

    fun durationRule(
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeDurationEditorRule(ruleId, matcher, data)
  }
}


private suspend fun makeTextEditorRuleWithSuggestions(
  ruleId: Long,
  ruleField: RuleField,
  matcher: Matcher<*>,
  data: MatcherData,
  showSuggestions: Boolean,
  getSuggestions: suspend ((String, TextSearch) -> List<String>)
): EditorRule {
  val newName = data.text.trim()
  val suggestions: List<String> = if (newName.isNotBlank() && showSuggestions) {
    getSuggestions(newName, matcher.textSearch)
  } else emptyList()

  return TextEditorRule(
    rule = Rule(ruleId, ruleField, matcher, data),
    editing = showSuggestions,
    nameValidity = newName.isValidName,
    suggestions = suggestions,
    capitalization = Capitalization.Words
  )
}

private suspend fun makeTitleEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  audioMediaDao: AudioMediaDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.Title,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  audioMediaDao.getTitleSuggestions(partial, textSearch)
    .onFailure { cause -> LOG.e(cause) { it("Error getting Title suggestions.") } }
    .getOrElse { emptyList() }
}

private suspend fun makeAlbumEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  albumDao: AlbumDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.Album,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  albumDao.getAlbumSuggestions(partial, textSearch)
    .getOrElse { cause ->
      LOG.e(cause) { it("Error getting Album suggestions.") }
      emptyList()
    }
}

private suspend fun makeArtistEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  artistDao: ArtistDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.Artist,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  artistDao.getArtistSuggestions(partial, textSearch)
    .getOrElse { cause ->
      LOG.e(cause) { it("Error getting Artist suggestions.") }
      emptyList()
    }
}

private suspend fun makeAlbumArtistEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  artistDao: ArtistDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.AlbumArtist,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  artistDao.getAlbumArtistSuggestions(partial, textSearch)
    .onFailure { cause -> LOG.e(cause) { it("Error getting AlbumArtist suggestions.") } }
    .getOrElse { emptyList() }
}

private suspend fun makeGenreEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  genreDao: GenreDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.Genre,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  genreDao.getGenreSuggestions(partial, textSearch)
    .onFailure { cause -> LOG.e(cause) { it("Error getting Genre suggestions") } }
    .getOrElse { emptyList() }
}

private suspend fun makeComposerEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  composerDao: ComposerDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId = ruleId,
  ruleField = RuleField.Composer,
  matcher = matcher,
  data = data,
  showSuggestions = update.showSuggestions
) { partial, textSearch ->
  composerDao.getComposerSuggestions(partial, textSearch)
    .onFailure { cause -> LOG.e(cause) { it("Error getting Composer suggestions") } }
    .getOrElse { emptyList() }
}

private fun makeCommentEditorRule(
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData
): EditorRule =
  TextEditorRule(
    rule = Rule(ruleId, RuleField.Comment, matcher, data),
    editing = false,
    nameValidity = SmartPlaylistEditorViewModel.NameValidity.IsValid,
    suggestions = emptyList(),
    capitalization = Capitalization.Sentences
  )

private suspend fun makePlaylistEditorRule(
  thisPlaylistId: PlaylistId,
  update: PlaylistEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  playlistDao: PlaylistDao
): EditorRule {
  val refersToThis: PlaylistIdList = playlistDao.smartPlaylistsReferringTo(thisPlaylistId)

  /**
   * Get the list of all playlists, less this smart playlist and less playlists
   * referring to this smart playlist
   */
  val dataList = update?.playlistMatcherData ?: playlistDao.getAllOfType()
    .onFailure { cause -> LOG.e(cause) { it("Error getting all playlists") } }
    .getOrElse { emptyList() }
    .asSequence()
    .map { idName -> PlaylistMatcherData(idName.id, idName.name, idName.type) }
    .filterNot { playlistMatcherData -> playlistMatcherData.id == thisPlaylistId }
    .filterNot { playlistMatcherData -> refersToThis.contains(playlistMatcherData.id) }
    .toList()

  val selectedIndex = if (matcher.willAccept(data)) {
    val fromMatcher = data.asPlaylistMatcherData
    dataList.indexOfFirst { playlistData -> playlistData == fromMatcher }
  } else 0

  val matcherData = if (dataList.isNotEmpty()) dataList[selectedIndex].asMatcherData else data

  return PlaylistEditorRule(
    Rule(ruleId, RuleField.Playlist, matcher, matcherData),
    selectedIndex,
    dataList
  )
}

private fun makeNumberEditorRule(
  update: NumberEditorRule?,
  ruleId: Long,
  field: RuleField,
  matcher: Matcher<*>,
  data: MatcherData
): EditorRule {
  return if (update != null) {
    if (matcher == NumberMatcher.IsInTheRange) {
      val low = update.firstText.asLong
      val high = update.secondText.asLong
      val firstLabel = if (low > -1) "Low $field" else fetch(R.string.MustBeGreaterOrEqualZero)
      val secondLabel = when {
        low >= high && low > -1 -> fetch(R.string.MustBeGreaterThanX, low)
        high == -1L -> fetch(R.string.MustBeGreaterOrEqualZero)
        else -> "High $field"
      }
      NumberEditorRule(
        rule = Rule(ruleId, field, matcher, data.copy(first = low, second = high)),
        firstText = update.firstText,
        secondText = update.secondText,
        firstLabel = firstLabel,
        secondLabel = secondLabel,
        firstIsError = low == -1L,
        secondIsError = low >= high
      )
    } else {
      val low = update.firstText.asLong
      val firstLabel = if (low > -1) field.toString() else fetch(R.string.MustBeGreaterOrEqualZero)
      NumberEditorRule(
        rule = Rule(ruleId, field, matcher, data.copy(first = low)),
        firstText = update.firstText,
        secondText = "",
        firstLabel = firstLabel,
        secondLabel = "",
        firstIsError = low == -1L,
        secondIsError = false
      )
    }
  } else {
    if (matcher == NumberMatcher.IsInTheRange) {
      val low = data.first
      val high = data.second
      val firstLabel = if (low > -1) "Low $field" else fetch(R.string.MustBeGreaterOrEqualZero)
      val secondLabel = when {
        low >= high && low > -1 -> fetch(R.string.MustBeGreaterThanX, low)
        high == -1L -> fetch(R.string.MustBeGreaterOrEqualZero)
        else -> "High $field"
      }
      NumberEditorRule(
        rule = Rule(ruleId, field, matcher, data.copy(first = low, second = high)),
        firstText = low.toString(),
        secondText = high.toString(),
        firstLabel = firstLabel,
        secondLabel = secondLabel,
        firstIsError = low == -1L,
        secondIsError = low >= high
      )
    } else {
      val value = data.first
      val firstLabel =
        if (value > -1) field.toString() else fetch(R.string.MustBeGreaterOrEqualZero)
      NumberEditorRule(
        rule = Rule(ruleId, field, matcher, data.copy(first = value)),
        firstText = value.toString(),
        secondText = "",
        firstLabel = firstLabel,
        secondLabel = "",
        firstIsError = value == -1L,
        secondIsError = false
      )
    }
  }
}

private fun makeDurationEditorRule(
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData
): EditorRule = DurationRule(Rule(ruleId, RuleField.Duration, matcher, data))

/** If this is a matcher against text or genre (same thing) then return how it searches wildcards */
private val Matcher<*>.textSearch: TextSearch
  get() = when (this) {
    is TextMatcher -> textSearch
    is GenreMatcher -> textSearch
    else -> TextSearch.Is
  }

private val String.isValidName: SmartPlaylistEditorViewModel.NameValidity
  get() = when {
    isBlank() -> SmartPlaylistEditorViewModel.NameValidity.IsInvalid
    else -> SmartPlaylistEditorViewModel.NameValidity.IsValid
  }

private val TextEditorRule?.showSuggestions: Boolean
  get() = this != null && editing

private fun makeDateEditorRule(
  update: EditorRule?,
  ruleId: Long,
  field: RuleField,
  matcher: Matcher<*>,
  data: MatcherData
): DateEditorRule {
  val text = (update as? DateEditorRule)?.textValue ?: data.first.toString()
  return DateEditorRule(Rule(ruleId, field, matcher, data), text)
}
