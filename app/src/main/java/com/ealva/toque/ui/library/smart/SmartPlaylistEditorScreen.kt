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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import coil.compose.rememberImagePainter
import com.ealva.toque.R
import com.ealva.toque.common.asPlaylistName
import com.ealva.toque.common.fetch
import com.ealva.toque.db.PlayListType
import com.ealva.toque.db.smart.AnyOrAll
import com.ealva.toque.db.smart.EndOfSmartPlaylistAction
import com.ealva.toque.db.smart.Matcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.SmartOrderBy
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.ui.common.AutoCompleteTextView
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.ScreenConfig
import com.ealva.toque.ui.library.BaseLibraryItemsScreen
import com.ealva.toque.ui.library.smart.EditorRule.DateEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.DurationRule
import com.ealva.toque.ui.library.smart.EditorRule.NumberEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.PlaylistEditorRule
import com.ealva.toque.ui.library.smart.EditorRule.TextEditorRule
import com.ealva.toque.ui.library.smart.SmartPlaylistEditorViewModel.SmartPlaylistData
import com.ealva.toque.ui.queue.DismissibleItem
import com.ealva.toque.ui.settings.AppBarTitle
import com.ealva.toque.ui.theme.toqueColors
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Immutable
@Parcelize
data class SmartPlaylistEditorScreen(
  private val playlistId: PlaylistId = PlaylistId.INVALID
) : BaseLibraryItemsScreen(), KoinComponent {
  @IgnoredOnParcel
  private lateinit var viewModel: SmartPlaylistEditorViewModel

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      viewModel = SmartPlaylistEditorViewModel(
        playlistId = playlistId,
        backstack = backstack,
        mainViewModel = lookup(),
        audioMediaDao = get(),
        playlistDao = get(),
        schemaDao = get()
      )
      add(viewModel)
    }
  }

  override fun navigateIfAllowed(command: () -> Unit) {
    viewModel.navigateIfAllowed(command)
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<SmartPlaylistEditorViewModel>()
    val playlistState = viewModel.playlistFlow.collectAsState()
    val config: ScreenConfig = LocalScreenConfig.current
    val playlist = playlistState.value

    Scaffold(
      modifier = Modifier
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
        .padding(bottom = config.getNavPlusBottomSheetHeight(isExpanded = true)),
      topBar = {
        TitleBar(
          back = { viewModel.goBack() },
          addRow = { viewModel.addRule() },
          restore = { viewModel.restoreOriginal() },
        )
      },
      floatingActionButton = {
        FAB(
          valid = playlist.savable,
          onClick = { viewModel.save() }
        )
      },
    ) {
      Editor(
        playlist = playlist,
        nameChanged = { newName -> viewModel.nameChanged(newName) },
        anyOrAllChanged = { anyOrAll -> viewModel.anyOrAllChanged(anyOrAll) },
        limitChanged = { limit -> viewModel.limitChanged(limit) },
        limitCheckboxChanged = { checked -> viewModel.limitCheckboxChange(checked) },
        selectByChanged = { selectBy -> viewModel.selectByChanged(selectBy) },
        endOfListActionChanged = { action -> viewModel.endOfListActionChanged(action) },
        ruleFieldChanged = { rule, ruleField -> viewModel.ruleFieldChanged(rule, ruleField) },
        matcherChanged = { rule, matcher -> viewModel.matcherChanged(rule, matcher) },
        ruleDataChanged = { rule, data -> viewModel.ruleDataChanged(rule, data) },
        deleteRule = { rule -> viewModel.deleteRule(rule) }
      )
    }
  }
}

@Composable
fun FAB(
  valid: Boolean,
  onClick: () -> Unit
) {
  if (valid) {
    FloatingActionButton(
      onClick = onClick,
      content = {
        Icon(
          painter = painterResource(id = R.drawable.ic_add),
          contentDescription = null,
          tint = Color.White
        )
      }
    )
  }
}

@Composable
private fun Editor(
  playlist: SmartPlaylistData,
  nameChanged: (String) -> Unit,
  anyOrAllChanged: (AnyOrAll) -> Unit,
  limitChanged: (String) -> Unit,
  limitCheckboxChanged: (Boolean) -> Unit,
  selectByChanged: (SmartOrderBy) -> Unit,
  endOfListActionChanged: (EndOfSmartPlaylistAction) -> Unit,
  ruleFieldChanged: (EditorRule, RuleField) -> Unit,
  matcherChanged: (EditorRule, Matcher<*>) -> Unit,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
  deleteRule: (EditorRule) -> Unit
) {
  Column {
    ProvideTextStyle(value = toqueTypography.body2) {
      Column(
        modifier =
        Modifier
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp)
      ) {
        NameField(playlist.editingName, playlist.nameValidity, nameChanged)
        AnyOrAllField(playlist.anyOrAll, anyOrAllChanged)
        PlaylistRules(
          playlist.ruleList,
          ruleFieldChanged,
          matcherChanged,
          ruleDataChanged,
          deleteRule
        )
        LimitRow(
          playlist.limitOn,
          playlist.limitText,
          playlist.limitIsValid,
          limitChanged,
          limitCheckboxChanged
        )
        LabeledComboBox(
          modifier = Modifier.padding(top = 8.dp),
          value = playlist.selectBy,
          possibleValues = SmartOrderBy.ALL_VALUES,
          valueChanged = selectByChanged,
          labelRes = R.string.Select_by
        )
        LabeledComboBox(
          modifier = Modifier.padding(top = 8.dp),
          value = playlist.endOfListAction,
          possibleValues = EndOfSmartPlaylistAction.ALL_VALUES,
          valueChanged = endOfListActionChanged,
          labelRes = R.string.At_list_end
        )
      }
    }
  }
}

@Composable
private fun NameField(
  name: String,
  nameValidity: SmartPlaylistEditorViewModel.NameValidity,
  nameChanged: (String) -> Unit
) {
  OutlinedTextField(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp),
    value = name,
    maxLines = 1,
    singleLine = true,
    onValueChange = { nameChanged(it) },
    isError = !nameValidity.isValid,
    label = if (nameValidity.isValid) {
      { Text(text = stringResource(id = R.string.Name)) }
    } else {
      { Text(text = nameValidity.toString()) }
    }
  )
}

@Composable
fun PlaylistRules(
  ruleList: List<EditorRule>,
  ruleFieldChanged: (EditorRule, RuleField) -> Unit,
  matcherChanged: (EditorRule, Matcher<*>) -> Unit,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
  deleteRule: (EditorRule) -> Unit
) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    ruleList.forEach {
      FieldMatcherEditorRow(
        editorRule = it,
        deletable = ruleList.size > 1,
        ruleFieldChanged = ruleFieldChanged,
        matcherChanged = matcherChanged,
        ruleDataChanged = ruleDataChanged,
        deleteRule = deleteRule
      )
    }
  }
}

@Composable
fun LimitRow(
  limitOn: Boolean,
  limitText: String,
  limitIsValid: Boolean,
  limitChanged: (String) -> Unit,
  limitCheckboxChanged: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = limitOn, onCheckedChange = { limitCheckboxChanged(it) })
    Text(
      modifier = Modifier.clickable { limitCheckboxChanged(!limitOn) },
      text = stringResource(id = R.string.Limit_to)
    )
    TextField(
      modifier = Modifier.padding(start = 2.dp),
      value = limitText,
      maxLines = 1,
      singleLine = true,
      label = if (limitOn && !limitIsValid) {
        { Text(text = fetch(R.string.MustBeGreaterThanX, 0)) }
      } else null,
      onValueChange = { value -> if (value.isDigitsOnly()) limitChanged(value) },
      isError = !limitIsValid,
      enabled = limitOn,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AnyOrAllField(value: AnyOrAll, anyOrAllChanged: (AnyOrAll) -> Unit) {
  Row(
    modifier = Modifier.padding(top = 4.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    LabeledComboBox(
      modifier = Modifier.padding(top = 8.dp),
      value = value,
      possibleValues = AnyOrAll.ALL_VALUES,
      valueChanged = anyOrAllChanged,
      labelRes = R.string.Match,
      dropDownWidth = 110.dp
    )
    Text(
      modifier = Modifier.padding(start = 4.dp),
      text = stringResource(id = R.string.of_the_following)
    )
  }
}

@Composable
private fun TitleBar(
  back: () -> Unit,
  restore: () -> Unit,
  addRow: () -> Unit
) {
  TopAppBar(
    title = { AppBarTitle(stringResource(id = R.string.SmartPlaylistEditor)) },
    backgroundColor = toqueColors.surface,
    modifier = Modifier.fillMaxWidth(),
    navigationIcon = {
      IconButton(onClick = back) {
        Icon(
          painter = rememberImagePainter(data = R.drawable.ic_arrow_left),
          contentDescription = "Back",
          modifier = Modifier.size(26.dp)
        )
      }
    },
    actions = {
      IconButton(onClick = restore) {
        Icon(
          painter = rememberImagePainter(data = R.drawable.ic_restore),
          contentDescription = "Restore original",
          tint = LocalContentColor.current
        )
      }
      IconButton(onClick = addRow) {
        Icon(
          painter = rememberImagePainter(data = R.drawable.ic_add_circle_outline),
          contentDescription = "Add Rule",
          tint = LocalContentColor.current
        )
      }
    }
  )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FieldMatcherEditorRow(
  editorRule: EditorRule,
  deletable: Boolean,
  ruleFieldChanged: (EditorRule, RuleField) -> Unit,
  matcherChanged: (EditorRule, Matcher<*>) -> Unit,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
  deleteRule: (EditorRule) -> Unit
) {
  val dismissState = rememberDismissState()
  if (dismissState.isDismissed(DismissDirection.EndToStart)) {
    deleteRule(editorRule)
    LaunchedEffect(key1 = editorRule.rule.id) {
      // Need to reset dismiss state so swipe doesn't occur again when undoing onDelete
      dismissState.snapTo(DismissValue.Default)
    }
  }

  if (deletable) {
    DismissibleItem(dismissState = dismissState, modifier = Modifier) {
      FieldMatcherEditor(
        editorRule,
        ruleFieldChanged,
        matcherChanged,
        ruleDataChanged,
      )
    }
  } else {
    FieldMatcherEditor(
      editorRule,
      ruleFieldChanged,
      matcherChanged,
      ruleDataChanged,
    )
  }
}

@Composable
private fun FieldMatcherEditor(
  editorRule: EditorRule,
  ruleFieldChanged: (EditorRule, RuleField) -> Unit,
  matcherChanged: (EditorRule, Matcher<*>) -> Unit,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
) {
  Column {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
      ComboBox(
        modifier = Modifier.weight(.45F),
        value = editorRule.rule.ruleField,
        possibleValues = RuleField.ALL_VALUES,
        valueChanged = { field -> ruleFieldChanged(editorRule, field) }
      )
      ComboBox(
        modifier = Modifier.weight(.55F),
        value = editorRule.rule.matcher,
        possibleValues = editorRule.rule.ruleField.matchers,
        valueChanged = { matcher -> matcherChanged(editorRule, matcher) }
      )
    }
    RuleEditor(editorRule, ruleDataChanged)
  }
}

@Composable
fun RuleEditor(
  editorRule: EditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  when (editorRule) {
    is TextEditorRule -> EditTextRule(editorRule, ruleDataChanged)
    is EditorRule.RatingEditorRule -> RatingEditor(editorRule, ruleDataChanged)
    is PlaylistEditorRule -> PlaylistEditor(editorRule, ruleDataChanged)
    is NumberEditorRule -> NumberEditor(editorRule, ruleDataChanged)
    is DateEditorRule -> DateEditor(editorRule, ruleDataChanged)
    is DurationRule -> DurationEditor(editorRule, ruleDataChanged)
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PlaylistEditor(
  editorRule: PlaylistEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  val options: List<PlaylistMatcherData> = editorRule.playlistMatcherData
  val value = if (options.isNotEmpty()) {
    options[if (editorRule.index in options.indices) editorRule.index else 0]
  } else PlaylistMatcherData(
    PlaylistId.INVALID,
    stringResource(R.string.ERRORNoPlaylists).asPlaylistName,
    PlayListType.System
  )

  ComboBox(
    modifier = Modifier.fillMaxWidth(),
    value = value,
    possibleValues = options,
    valueChanged = { playlistData -> ruleDataChanged(editorRule, playlistData.asMatcherData) }
  )
}

@Composable
fun EditTextRule(
  editorRule: TextEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
) {
  val rule = editorRule.rule
  val validity = editorRule.nameValidity

  AutoCompleteTextView(
    modifier = Modifier.fillMaxWidth(),
    query = rule.data.text,
    suggestions = editorRule.suggestions,
    isError = validity.isNotValid,
    onTextChanged = { text -> ruleDataChanged(editorRule, rule.data.copy(text = text)) },
    onSelected = { text ->
      ruleDataChanged(editorRule.copy(editing = false), rule.data.copy(text = text))
    },
    onDoneActionClick = { ruleDataChanged(editorRule.copy(editing = false), rule.data) },
    isFocused = { isFocused -> ruleDataChanged(editorRule.copy(editing = isFocused), rule.data) },
    label = { Text(if (validity.isNotValid) validity.toString() else rule.ruleField.toString()) }
  )
}
