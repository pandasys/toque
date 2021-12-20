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

package com.ealva.toque.ui.library

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.ComposerDao
import com.ealva.toque.db.GenreDao
import com.ealva.toque.db.TextSearch
import com.ealva.toque.db.smart.GenreMatcher
import com.ealva.toque.db.smart.Matcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RatingMatcher
import com.ealva.toque.db.smart.Rule
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.TextMatcher
import com.ealva.toque.log._e
import com.ealva.toque.ui.library.EditorRule.SimpleEditorRule
import com.ealva.toque.ui.library.EditorRule.TextEditorRule
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(EditorRule::class)

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
  ) : EditorRule

  @Immutable
  @Parcelize
  data class SimpleEditorRule(
    override val rule: Rule,
  ) : EditorRule

  companion object {
    suspend fun titleEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      audioMediaDao: AudioMediaDao
    ): EditorRule = makeTitleEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      audioMediaDao
    )

    suspend fun albumEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      albumDao: AlbumDao
    ): EditorRule = makeAlbumEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      albumDao
    )

    suspend fun artistEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      audioMediaDao: AudioMediaDao
    ): EditorRule = makeArtistEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      audioMediaDao
    )

    suspend fun albumArtistEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      audioMediaDao: AudioMediaDao
    ): EditorRule = makeAlbumArtistEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      audioMediaDao
    )

    suspend fun genreEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      genreDao: GenreDao
    ): EditorRule = makeGenreEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      genreDao
    )

    suspend fun composerEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData,
      composerDao: ComposerDao
    ): EditorRule = makeComposerEditorRule(
      update as? TextEditorRule,
      ruleId,
      matcher,
      data,
      composerDao
    )

    fun commentEditorRule(
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeCommentEditorRule(ruleId, matcher, data)

    fun ratingEditorRule(
      update: EditorRule?,
      ruleId: Long,
      matcher: Matcher<*>,
      data: MatcherData
    ): EditorRule = makeRatingEditorRule(update, ruleId, matcher, data)
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
  val rule = Rule(ruleId, ruleField, matcher, data)
  val newName = data.text
  LOG._e { it("showSuggestions=%s", showSuggestions) }
  val suggestions: List<String> = if (newName.isNotBlank() && showSuggestions) {
    getSuggestions(newName, matcher.textSearch)
  } else emptyList()

  return TextEditorRule(
    rule = rule,
    editing = showSuggestions,
    nameValidity = newName.isValidName,
    suggestions = suggestions
  )
}

private suspend fun makeTitleEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  audioMediaDao: AudioMediaDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId,
  RuleField.Title,
  matcher,
  data,
  update.showSuggestions(data)
) { partial, textSearch ->
  when (val result = audioMediaDao.getTitleSuggestions(partial, textSearch)) {
    is Ok -> result.value
    is Err -> {
      LOG.e { it("Error getting suggestions. %s", result.error) }
      emptyList()
    }
  }
}

private suspend fun makeAlbumEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  audioMediaDao: AlbumDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId,
  RuleField.Album,
  matcher,
  data,
  update.showSuggestions(data)
) { partial, textSearch ->
  when (val result = audioMediaDao.getAlbumSuggestions(partial, textSearch)) {
    is Ok -> result.value
    is Err -> {
      LOG.e { it("Error getting suggestions. %s", result.error) }
      emptyList()
    }
  }
}

private suspend fun makeArtistEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  audioMediaDao: AudioMediaDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId,
  RuleField.Artist,
  matcher,
  data,
  update.showSuggestions(data)
) { partial, textSearch ->
  when (val result = audioMediaDao.getArtistSuggestions(partial, textSearch)) {
    is Ok -> result.value
    is Err -> {
      LOG.e { it("Error getting suggestions. %s", result.error) }
      emptyList()
    }
  }
}

private suspend fun makeAlbumArtistEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  audioMediaDao: AudioMediaDao
): EditorRule {
  return makeTextEditorRuleWithSuggestions(
    ruleId,
    RuleField.AlbumArtist,
    matcher,
    data,
    update.showSuggestions(data)
  ) { partial, textSearch ->
    when (val result = audioMediaDao.getAlbumArtistSuggestions(partial, textSearch)) {
      is Ok -> result.value
      is Err -> {
        LOG.e { it("Error getting suggestions. %s", result.error) }
        emptyList()
      }
    }
  }
}

private suspend fun makeGenreEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  genreDao: GenreDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId,
  RuleField.Genre,
  matcher,
  data,
  update.showSuggestions(data)
) { partial, textSearch ->
  when (val result = genreDao.getGenreSuggestions(partial, textSearch)) {
    is Ok -> result.value
    is Err -> {
      LOG.e { it("Error getting suggestions. %s", result.error) }
      emptyList()
    }
  }
}

private suspend fun makeComposerEditorRule(
  update: TextEditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData,
  composerDao: ComposerDao
): EditorRule = makeTextEditorRuleWithSuggestions(
  ruleId,
  RuleField.Composer,
  matcher,
  data,
  update.showSuggestions(data)
) { partial, textSearch ->
  when (val result = composerDao.getComposerSuggestions(partial, textSearch)) {
    is Ok -> result.value
    is Err -> {
      LOG.e { it("Error getting suggestions. %s", result.error) }
      emptyList()
    }
  }
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
    suggestions = emptyList()
  )


private fun makeRatingEditorRule(
  update: EditorRule?,
  ruleId: Long,
  matcher: Matcher<*>,
  data: MatcherData
): EditorRule {
  val ratingMatcher = matcher as RatingMatcher

  return SimpleEditorRule(
    Rule(
      ruleId,
      RuleField.Rating,
      ratingMatcher,
      ratingMatcher.sanitizeData(update?.rule?.data, data)
    )
  )
  /*
      fun ratingEditorRule(ruleId: Long, matcher: Matcher<*>, data: MatcherData): EditorRule {
      val matcherData = when (matcher as RatingMatcher) {
        RatingMatcher.IsInTheRange -> {
          data
        }
        else -> data.copy(
          first = data.first.toInt().toRating().coerceIn(Rating.VALID_RANGE).value.toLong()
        )
      }
      return SimpleEditorRule(Rule(ruleId, RuleField.Rating, matcher, matcherData), false)
    }
  }
}
   */
}

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

private fun TextEditorRule?.showSuggestions(data: MatcherData): Boolean =
//  this != null && editing
this != null && (editing || rule.data != data)
