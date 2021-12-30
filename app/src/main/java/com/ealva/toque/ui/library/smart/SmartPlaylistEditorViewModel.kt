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
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Limit
import com.ealva.toque.common.PlaylistName
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.PlaylistDao
import com.ealva.toque.db.SchemaDao
import com.ealva.toque.db.SchemaName
import com.ealva.toque.db.asSchemaName
import com.ealva.toque.db.smart.AnyOrAll
import com.ealva.toque.db.smart.EndOfSmartPlaylistAction
import com.ealva.toque.db.smart.Matcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.Rule
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.SmartOrderBy
import com.ealva.toque.db.smart.SmartPlaylist
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.navigation.AllowableNavigation
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.persist.isValid
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.library.smart.EditorRule.Companion.albumArtistRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.albumRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.artistRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.commentRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.composerRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.dateAddedRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.discCountRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.genreRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.lastPlayedRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.lastSkippedRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.playCountRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.playlistRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.ratingRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.skipCountRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.titleRule
import com.ealva.toque.ui.library.smart.EditorRule.Companion.yearRule
import com.ealva.toque.ui.library.smart.SmartPlaylistEditorViewModel.NameValidity
import com.ealva.toque.ui.library.smart.SmartPlaylistEditorViewModel.SmartPlaylistData
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.Notification
import com.ealva.toque.ui.nav.back
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(SmartPlaylistEditorViewModel::class)

interface SmartPlaylistEditorViewModel : AllowableNavigation {
  enum class NameValidity(@StringRes val stringRes: Int) {
    IsValid(R.string.None),
    IsInvalid(R.string.Invalid),
    IsReserved(R.string.Reserved);

    inline val isValid: Boolean get() = this === IsValid
    inline val isNotValid: Boolean get() = this !== IsValid

    override fun toString(): String = fetch(stringRes)
  }

  @Immutable
  @Parcelize
  data class SmartPlaylistData(
    /** The starting ID - invalid if new playlist, else editing/updating existing list */
    val id: PlaylistId,

    val original: SmartPlaylist,

    /** The original playlist name - which could be user chosen or generic "Playlist 1" type name */
    val originalName: PlaylistName,
    /** The name as it exists in the editor - could be invalid (reserved or blank) */
    val editingName: String,
    /** Current validity of [editingName] */
    val nameValidity: NameValidity,
    val anyOrAll: AnyOrAll,
    val limit: Limit,
    /** Has the user indicated a limit */
    val limitOn: Boolean,
    /** Current text in the limit edit field */
    val limitText: String,
    /** Is [limitText] a valid number  */
    val limitIsValid: Boolean,
    val selectBy: SmartOrderBy,
    val endOfListAction: EndOfSmartPlaylistAction,
    /** The list of SmartPlaylist rules and an editor for modifying them */
    val ruleList: List<EditorRule> = ArrayList()
  ) : Parcelable {
    val hasBeenEdited: Boolean
      get() = asSmartPlaylist != original

    inline val hasNotBeenEdited: Boolean get() = !hasBeenEdited

    val savable: Boolean
      get() = isValid && hasBeenEdited

    val isValid: Boolean
      get() = nameValidity.isValid && ruleList.isNotEmpty() && ruleList.all { it.isValid }
  }

  val playlistFlow: StateFlow<SmartPlaylistData>

  fun goBack()
  fun addRule()
  fun restoreOriginal()
  fun nameChanged(newName: String)
  fun anyOrAllChanged(anyOrAll: AnyOrAll)
  fun limitCheckboxChange(checked: Boolean)
  fun limitChanged(limit: String)
  fun selectByChanged(selectBy: SmartOrderBy)
  fun endOfListActionChanged(action: EndOfSmartPlaylistAction)
  fun ruleFieldChanged(updateRule: EditorRule, ruleField: RuleField)
  fun matcherChanged(updateRule: EditorRule, matcher: Matcher<*>)
  fun ruleDataChanged(updateRule: EditorRule, newData: MatcherData)

  fun save()
  fun deleteRule(editorRule: EditorRule)

  companion object {
    operator fun invoke(
      playlistId: PlaylistId,
      backstack: Backstack,
      mainViewModel: MainViewModel,
      audioMediaDao: AudioMediaDao,
      playlistDao: PlaylistDao,
      schemaDao: SchemaDao,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): SmartPlaylistEditorViewModel = SmartPlaylistEditorViewModelImpl(
      playlistId,
      backstack,
      mainViewModel,
      audioMediaDao,
      playlistDao,
      schemaDao,
      dispatcher
    )
  }
}

private class SmartPlaylistEditorViewModelImpl(
  private val playlistId: PlaylistId,
  private val backstack: Backstack,
  private val mainViewModel: MainViewModel,
  private val audioMediaDao: AudioMediaDao,
  private val playlistDao: PlaylistDao,
  private val schemaDao: SchemaDao,
  private val dispatcher: CoroutineDispatcher
) : SmartPlaylistEditorViewModel, ScopedServices.Registered, Bundleable,
  ScopedServices.HandlesBack {
  private lateinit var scope: CoroutineScope
  private lateinit var original: SmartPlaylist

  private val allPlaylistNames = mutableListOf<PlaylistName>()
  private val allViewNames = mutableListOf<SchemaName>()
  override val playlistFlow = MutableStateFlow(makeEmptySmartPlaylistData())
  private inline val currentPlaylistData get() = playlistFlow.value
  private var initialized = false
  private val ruleUpdateFlow = MutableSharedFlow<ModelRuleUpdate>(extraBufferCapacity = 10)

  sealed interface ModelRuleUpdate {
    data class RuleFieldChanged(val updateRule: EditorRule, val field: RuleField) : ModelRuleUpdate
    data class MatcherChanged(val updateRule: EditorRule, val matcher: Matcher<*>) : ModelRuleUpdate
    data class RuleDataChanged(val updateRule: EditorRule, val data: MatcherData) : ModelRuleUpdate
  }

  private fun makeEmptySmartPlaylistData(): SmartPlaylistData {
    return SmartPlaylistData(
      id = PlaylistId.INVALID,
      original = makeEmptySmartPlaylist(),
      originalName = "".asPlaylistName,
      editingName = "",
      nameValidity = NameValidity.IsInvalid,
      anyOrAll = AnyOrAll.All,
      limit = Limit.NoLimit,
      limitOn = false,
      limitText = "",
      limitIsValid = true,
      selectBy = SmartOrderBy.None,
      endOfListAction = EndOfSmartPlaylistAction.EndOfQueueAction,
      ruleList = emptyList()
    )
  }

  override fun onServiceRegistered() {
    if (!::scope.isInitialized) scope = CoroutineScope(SupervisorJob() + dispatcher)
    collectRuleUpdateFlow(scope)
    if (!initialized) {
      initialized = true
      allPlaylistNames.clear()
      allViewNames.clear()
      scope.launch {
        when (val result = playlistDao.getAllOfType()) {
          is Ok -> allPlaylistNames.addAll(result.value.asSequence().map { it.name })
          is Err -> LOG.e { it("Error getting playlist names. %s", result.error) }
        }
        when (val result = schemaDao.getTableAndViewNames()) {
          is Ok -> allViewNames.addAll(result.value)
          is Err -> LOG.e { it("Error getting schema names. %s", result.error) }
        }
        if (playlistId.isValid) {
          when (val result = playlistDao.getSmartPlaylist(playlistId)) {
            is Ok -> setOriginal(result.value)
            is Err -> {
              LOG.e { it("Error getting Smart Playlist. %s", result.error) }
              setOriginal(makeDefaultSmartPlaylist())
            }
          }
        } else {
          setOriginal(makeDefaultSmartPlaylist())
        }
      }
    }
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  private var allowExit = false
  override fun onBackEvent(): Boolean = currentPlaylistData.let { playlistData ->
    if (allowExit || (playlistData.isValid && playlistData.hasNotBeenEdited)) false else true.also {
      showExitPrompt(playlistData) {
        allowExit = true
        scope.launch { backstack.goBack() }
      }
    }
  }

  override fun goBack() = backstack.back()

  override fun addRule() = playlistFlow.update {
    it.copy(ruleList = it.ruleList.toMutableList().apply { add(makeDefaultRule()) })
  }

  override fun restoreOriginal() {
    scope.launch {
      playlistFlow.value = original.toSmartPlaylistData()
    }
  }

  override fun nameChanged(newName: String) = playlistFlow.update {
    it.copy(
      editingName = newName,
      nameValidity = playlistNameIsValid(it.originalName, newName)
    )
  }

  override fun anyOrAllChanged(anyOrAll: AnyOrAll) =
    playlistFlow.update { it.copy(anyOrAll = anyOrAll) }

  override fun limitCheckboxChange(checked: Boolean) {
    if (checked) {
      playlistFlow.update {
        val currentLimit = it.limitText.toLongOrNull()
        if (it.limitIsValid && currentLimit != null && currentLimit > 0) {
          it.copy(
            limit = Limit(currentLimit),
            limitText = currentLimit.toString(),
            limitOn = true
          )
        } else {
          it.copy(
            limit = Limit.NoLimit,
            limitOn = true,
            limitText = "",
            limitIsValid = false
          )
        }
      }
    } else {
      playlistFlow.update {
        it.copy(
          limit = Limit.NoLimit,
          limitOn = false,
          limitText = "",
          limitIsValid = false
        )
      }
    }
  }

  override fun limitChanged(limit: String) {
    val parsedLimit = limit.toIntOrNull()
    if (parsedLimit != null && parsedLimit > 0) {
      playlistFlow.update {
        it.copy(
          limit = Limit(limit.toLong()),
          limitText = limit,
          limitIsValid = true
        )
      }
    } else playlistFlow.update {
      it.copy(
        limitIsValid = false,
        limitText = limit
      )
    }
  }

  override fun selectByChanged(selectBy: SmartOrderBy) =
    playlistFlow.update { it.copy(selectBy = selectBy) }

  override fun endOfListActionChanged(action: EndOfSmartPlaylistAction) =
    playlistFlow.update { it.copy(endOfListAction = action) }

  private fun emitRuleUpdate(update: ModelRuleUpdate) {
    scope.launch { ruleUpdateFlow.emit(update) }
  }

  override fun ruleFieldChanged(updateRule: EditorRule, ruleField: RuleField) {
    emitRuleUpdate(ModelRuleUpdate.RuleFieldChanged(updateRule, ruleField))
  }

  private suspend fun doRuleFieldChange(update: ModelRuleUpdate.RuleFieldChanged) {
    playlistFlow.update {
      it.copy(
        ruleList = it.ruleList.map { current ->
          changeRuleField(
            current,
            update.updateRule,
            update.field
          )
        }
      )
    }
  }

  /**
   * When changing the [ruleField] make sure to also change [Rule.matcher] as different fields
   * have different classes of matchers.
   */
  private suspend fun changeRuleField(
    current: EditorRule,
    updateRule: EditorRule,
    ruleField: RuleField
  ): EditorRule = if (current.rule.id == updateRule.rule.id) {
    val matcher = ruleField.matchers.first()
    makeEditorRule(
      update = null,
      ruleId = current.rule.id,
      field = ruleField,
      matcher = matcher,
      data = matcher.acceptableData(updateRule.rule.data),
    )
  } else {
    current
  }

  override fun matcherChanged(updateRule: EditorRule, matcher: Matcher<*>) {
    emitRuleUpdate(ModelRuleUpdate.MatcherChanged(updateRule, matcher))
  }

  private suspend fun doMatcherChanged(update: ModelRuleUpdate.MatcherChanged) {
    playlistFlow.update {
      it.copy(ruleList = it.ruleList.map { current ->
        changeMatcher(current, update.updateRule, update.matcher)
      })
    }
  }

  private suspend fun changeMatcher(
    current: EditorRule,
    updateRule: EditorRule,
    matcher: Matcher<*>
  ): EditorRule = if (current.rule.id == updateRule.rule.id) {
    makeEditorRule(
      update = null,
      ruleId = current.rule.id,
      field = current.rule.ruleField,
      matcher = matcher,
      data = matcher.acceptableData(updateRule.rule.data),
    )
  } else {
    current
  }

  override fun ruleDataChanged(updateRule: EditorRule, newData: MatcherData) {
    emitRuleUpdate(ModelRuleUpdate.RuleDataChanged(updateRule, newData))
  }

  private suspend fun doRuleDataChanged(update: ModelRuleUpdate.RuleDataChanged) {
    val updateRule = update.updateRule
    val newData = update.data
    LOG._e { it("newData %s", newData) }
    playlistFlow.update { smartPlaylistData ->
      smartPlaylistData.copy(
        ruleList = smartPlaylistData.ruleList.map { current ->
          if (updateRule.rule.id == current.rule.id) makeEditorRule(
            update = updateRule,
            updateRule.rule.id,
            updateRule.rule.ruleField,
            updateRule.rule.matcher,
            newData,
          ) else current
        }
      )
    }
  }

  override fun save() {
    val playlistData = currentPlaylistData
    if (playlistData.savable) {
      scope.launch { doSave(playlistData) }
    } else {
      LOG.e { it("Playlist not savable: %s", playlistData.asSmartPlaylist) }
    }
  }

  override fun deleteRule(editorRule: EditorRule) {
    playlistFlow.update {
      it.copy(
        ruleList = it.ruleList.mapNotNull { current ->
          if (current.rule.id == editorRule.rule.id) null else current
        }
      )
    }
  }

  private fun showPrompt(prompt: DialogPrompt) {
    mainViewModel.prompt(prompt)
  }

  private fun clearPrompt() {
    mainViewModel.clearPrompt()
  }


  override fun navigateIfAllowed(command: () -> Unit) {
    val playlistData = currentPlaylistData
    if (playlistData.let { it.isValid && it.hasNotBeenEdited }) {
      command()
    } else {
      // Exit and discard all changes? Exit, Cancel, Save (if savable)
      showExitPrompt(playlistData, command)
    }
  }

  private fun showExitPrompt(
    playlistData: SmartPlaylistData,
    command: () -> Unit
  ) {
    showPrompt(
      DialogPrompt(
        prompt = {
          ExitDiscardCancelSave(
            isSavable = playlistData.savable,
            onCancel = { clearPrompt() },
            onSave = {
              save()
              clearPrompt()
              command()
            },
            onExit = {
              clearPrompt()
              command()
            }
          )
        }
      )
    )
  }

  private suspend fun doSave(playlistData: SmartPlaylistData) {
    when (val result = playlistDao.createOrUpdateSmartPlaylist(playlistData.asSmartPlaylist)) {
      is Ok -> {
        setOriginal(result.value)
        mainViewModel.notify(Notification(fetch(R.string.SavedPlaylist, playlistData.editingName)))
      }
      is Err -> {
        LOG.e { it("Couldn't create playlist. %s", result.error) }
        mainViewModel.notify(
          Notification(fetch(R.string.ErrorSavingPlaylist, playlistData.editingName))
        )
      }
    }
  }

  private fun collectRuleUpdateFlow(scope: CoroutineScope) {
    ruleUpdateFlow
      .onEach { modelRuleUpdate -> handleRuleUpdate(modelRuleUpdate) }
      .catch { cause -> LOG.e(cause) { it("Error processing rule updates.") } }
      .onCompletion { LOG._i { it("Completed rule update flow") } }
      .launchIn(scope)
  }

  private suspend fun handleRuleUpdate(modelRuleUpdate: ModelRuleUpdate) {
    when (modelRuleUpdate) {
      is ModelRuleUpdate.RuleFieldChanged -> doRuleFieldChange(modelRuleUpdate)
      is ModelRuleUpdate.MatcherChanged -> doMatcherChanged(modelRuleUpdate)
      is ModelRuleUpdate.RuleDataChanged -> doRuleDataChanged(modelRuleUpdate)
    }
  }

  private suspend fun SmartPlaylist.toSmartPlaylistData(): SmartPlaylistData {
    val smartPlaylist = this
    return SmartPlaylistData(
      id = smartPlaylist.id,
      original = smartPlaylist,
      originalName = smartPlaylist.name,
      editingName = smartPlaylist.name.value,
      nameValidity = NameValidity.IsValid,
      anyOrAll = smartPlaylist.anyOrAll,
      limit = smartPlaylist.limit,
      limitOn = smartPlaylist.limit.value > 0,
      limitText = if (smartPlaylist.limit.isValid) smartPlaylist.limit.value.toString() else "",
      limitIsValid = true,
      selectBy = smartPlaylist.selectBy,
      endOfListAction = smartPlaylist.endOfListAction,
      ruleList = smartPlaylist.ruleList.map { rule -> rule.toEditorRule() }
    )
  }

  private suspend fun Rule.toEditorRule(): EditorRule =
    makeEditorRule(null, id, ruleField, matcher, data)

  private suspend fun setOriginal(playlist: SmartPlaylist) {
    original = playlist
    playlistFlow.value = playlist.toSmartPlaylistData()
  }

  private fun makeDefaultSmartPlaylist(): SmartPlaylist = SmartPlaylist(
    id = PlaylistId.INVALID,
    name = getAvailablePlaylistName().asPlaylistName,
    anyOrAll = AnyOrAll.All,
    limit = Limit.NoLimit,
    selectBy = SmartOrderBy.None,
    endOfListAction = EndOfSmartPlaylistAction.EndOfQueueAction,
    ruleList = listOf(
      Rule(
        getNextId(),
        RuleField.Title,
        RuleField.Title.matchers[0],
        MatcherData.EMPTY
      )
    )
  )

  private fun makeEmptySmartPlaylist(): SmartPlaylist = SmartPlaylist(
    id = PlaylistId.INVALID,
    name = "".asPlaylistName,
    anyOrAll = AnyOrAll.All,
    limit = Limit.NoLimit,
    selectBy = SmartOrderBy.None,
    endOfListAction = EndOfSmartPlaylistAction.EndOfQueueAction,
    ruleList = emptyList()
  )

  private fun getAvailablePlaylistName(): String {
    val prefix = "Playlist "
    var num = 1
    var possible = "$prefix$num"
    while (nameIsReserved(possible)) {
      num++
      possible = "$prefix$num"
    }
    return possible
  }

  private fun playlistNameIsValid(
    original: PlaylistName,
    name: String
  ): NameValidity {
    return when {
      name.isBlank() -> NameValidity.IsInvalid
      name.equals(original.value, ignoreCase = true) -> NameValidity.IsValid
      nameIsReserved(name) -> NameValidity.IsReserved
      else -> NameValidity.IsValid
    }
  }

  private fun nameIsReserved(name: String): Boolean = name.trim().let { value ->
    name.startsWith("sqlite_", ignoreCase = true) ||
        allViewNames.any { it.value.equals(value, ignoreCase = true) } ||
        allPlaylistNames.any { it.value.equals(value, ignoreCase = true) }
  }

  private fun makeDefaultRule(): EditorRule {
    val data = MatcherData.EMPTY
    val rule = Rule(getNextId(), RuleField.Title, RuleField.Title.matchers[0], data)
    return EditorRule.TextEditorRule(
      rule = rule,
      nameValidity = data.text.isValidName,
      suggestions = emptyList(),
      editing = false
    )
  }

  private fun getNextId(): Long = (playlistFlow
    .value
    .ruleList
    .maxByOrNull { edit -> edit.rule.id }
    ?.rule
    ?.id
    ?: 0) + 1

  private suspend fun makeEditorRule(
    update: EditorRule?,
    ruleId: Long,
    field: RuleField,
    matcher: Matcher<*>,
    data: MatcherData
  ): EditorRule = when (field) {
    RuleField.Title -> titleRule(update, ruleId, matcher, data, audioMediaDao)
    RuleField.Album -> albumRule(update, ruleId, matcher, data, audioMediaDao.albumDao)
    RuleField.Artist -> artistRule(update, ruleId, matcher, data, audioMediaDao)
    RuleField.AlbumArtist -> albumArtistRule(update, ruleId, matcher, data, audioMediaDao)
    RuleField.Genre -> genreRule(update, ruleId, matcher, data, audioMediaDao.genreDao)
    RuleField.Composer -> composerRule(update, ruleId, matcher, data, audioMediaDao.composerDao)
    RuleField.Rating -> ratingRule(ruleId, matcher, data)
    RuleField.Year -> yearRule(update, ruleId, matcher, data)
    RuleField.DateAdded -> dateAddedRule(update, ruleId, matcher, data)
    RuleField.PlayCount -> playCountRule(update, ruleId, matcher, data)
    RuleField.LastPlayed -> lastPlayedRule(update, ruleId, matcher, data)
    RuleField.SkipCount -> skipCountRule(update, ruleId, matcher, data)
    RuleField.LastSkipped -> lastSkippedRule(update, ruleId, matcher, data)
    RuleField.Duration -> EditorRule.durationRule(ruleId, matcher, data)
    RuleField.Playlist ->
      playlistRule(playlistId, update, ruleId, matcher, data, audioMediaDao.playlistDao)
    RuleField.Comment -> commentRule(ruleId, matcher, data)
    RuleField.DiscCount -> discCountRule(update, ruleId, matcher, data)
  }

  override fun toBundle(): StateBundle = StateBundle().apply {
    putParcelable(KEY_ORIGINAL, original)
    putStringArrayList(
      KEY_ALL_PLAYLIST_NAMES,
      allPlaylistNames.mapTo(ArrayList(allPlaylistNames.size)) { name -> name.value }
    )
    putStringArrayList(
      KEY_ALL_VIEW_NAMES,
      allViewNames.mapTo(ArrayList(allViewNames.size)) { schemaName -> schemaName.value }
    )
    putParcelable(KEY_EDITOR_DATA, currentPlaylistData)
  }

  override fun fromBundle(bundle: StateBundle?) {
    if (!::scope.isInitialized) scope = CoroutineScope(SupervisorJob() + dispatcher)
    original = bundle?.getParcelable(KEY_ORIGINAL) ?: makeDefaultSmartPlaylist()
    bundle?.getStringArrayList(KEY_ALL_PLAYLIST_NAMES)?.let { playlistNames ->
      allPlaylistNames.clear()
      playlistNames.mapTo(allPlaylistNames) { string -> string.asPlaylistName }
    }
    bundle?.getStringArrayList(KEY_ALL_VIEW_NAMES)?.let { viewNames ->
      allViewNames.clear()
      viewNames.mapTo(allViewNames) { string -> string.asSchemaName }
    }
    scope.launch {
      playlistFlow.value = bundle?.getParcelable(KEY_EDITOR_DATA) ?: original.toSmartPlaylistData()
    }
    initialized = true
  }

  companion object {
    private const val KEY_ORIGINAL = "SmartEditor_OriginalSmartPlaylist"
    private const val KEY_ALL_PLAYLIST_NAMES = "SmartEditor_AllPlaylistNames"
    private const val KEY_ALL_VIEW_NAMES = "SmartEditor_AllViewNames"
    private const val KEY_EDITOR_DATA = "SmartEditor_EditorData"
  }
}

private val SmartPlaylistData.asSmartPlaylist: SmartPlaylist
  get() {
    require(nameValidity.isValid) { "$editingName is not valid" }
    return SmartPlaylist(
      id = id,
      name = editingName.asPlaylistName,
      anyOrAll = anyOrAll,
      limit = limit,
      selectBy = selectBy,
      endOfListAction = endOfListAction,
      ruleList = ruleList.mapTo(ArrayList(ruleList.size)) { editorRule ->
        editorRule.rule.also { rule -> require(rule.isValid) { "Rule is not valid: $rule" } }
      }
    ).also { playlist -> require(playlist.isValid) { "Playlist is not valid. $playlist" } }
  }

private val String.isValidName: NameValidity
  get() = when {
    isBlank() -> NameValidity.IsInvalid
    else -> NameValidity.IsValid
  }
