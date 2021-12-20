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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.common.Rating
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.toRating
import com.ealva.toque.common.toStarRating
import com.ealva.toque.db.smart.AnyOrAll
import com.ealva.toque.db.smart.EndOfSmartPlaylistAction
import com.ealva.toque.db.smart.Matcher
import com.ealva.toque.db.smart.MatcherData
import com.ealva.toque.db.smart.RatingMatcher
import com.ealva.toque.db.smart.RuleField
import com.ealva.toque.db.smart.SmartOrderBy
import com.ealva.toque.log._e
import com.ealva.toque.persist.PlaylistId
import com.ealva.toque.ui.common.AutoCompleteTextView
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.config.ScreenConfig
import com.ealva.toque.ui.library.SmartPlaylistEditorViewModel.SmartPlaylistData
import com.ealva.toque.ui.queue.DismissibleItem
import com.ealva.toque.ui.settings.AppBarTitle
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(SmartPlaylistEditorScreen::class)

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
        ruleDataChanged = { rule, data ->
          LOG._e { it("ruleDataChanged rule:%s", rule) }
          viewModel.ruleDataChanged(rule, data)
        },
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
    ProvideTextStyle(value = MaterialTheme.typography.body2) {
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
        LabeledDropdown(
          value = playlist.selectBy,
          allValues = SmartOrderBy.values(),
          valueChanged = selectByChanged,
          labelRes = R.string.Select_by
        )
        LabeledDropdown(
          value = playlist.endOfListAction,
          allValues = EndOfSmartPlaylistAction.values(),
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
        { Text(text = stringResource(id = R.string.PositiveNumberRequired)) }
      } else null,
      onValueChange = { limitChanged(it) },
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
    LabeledDropdown(
      value = value,
      allValues = AnyOrAll.values(),
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
    backgroundColor = MaterialTheme.colors.surface,
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
fun <T : Enum<T>> LabeledDropdown(
  value: T,
  allValues: Array<T>,
  valueChanged: (T) -> Unit,
  @StringRes labelRes: Int,
  dropDownWidth: Dp = Dp.Unspecified
) {
  val options = allValues.toList()
  var expanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.padding(top = 8.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.padding(end = 4.dp),
      text = stringResource(id = labelRes)
    )
    ExposedDropdownMenuBox(
      modifier = Modifier.width(dropDownWidth),
      expanded = expanded,
      onExpandedChange = { expanded = !expanded }
    ) {
      CustomTextField(
        value = value.toString(),
        readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      )
      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        options.forEach { option ->
          DropdownMenuItem(
            onClick = {
              expanded = false
              valueChanged(option)
            }
          ) {
            Text(text = option.toString())
          }
        }
      }
    }
  }
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
      RuleEditorDropdown(
        modifier = Modifier.weight(.45F),
        value = editorRule.rule.ruleField,
        allValues = RuleField.values().asList(),
        valueChanged = { ruleFieldChanged(editorRule, it) }
      )
      RuleEditorDropdown(
        modifier = Modifier.weight(.55F),
        value = editorRule.rule.matcher,
        allValues = editorRule.rule.ruleField.matchers,
        valueChanged = { matcherChanged(editorRule, it) }
      )
    }
    RuleEditor(editorRule, ruleDataChanged)
  }
}

@Composable
fun RuleEditor(
  rule: EditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit
) {
  when {
    rule is EditorRule.TextEditorRule -> EditTextRule(rule, ruleDataChanged)
    rule.rule.ruleField == RuleField.Rating -> EditRating(rule, ruleDataChanged)
  }
}

@Composable
fun EditRating(editorRule: EditorRule, ruleDataChanged: (EditorRule, MatcherData) -> Unit) {
  when (editorRule.rule.matcher as RatingMatcher) {
    RatingMatcher.IsInTheRange -> RatingRangeEditor(editorRule, ruleDataChanged)
    else -> SingleRatingEditor(editorRule, ruleDataChanged)
  }
}

@Composable
fun SingleRatingEditor(editorRule: EditorRule, ruleDataChanged: (EditorRule, MatcherData) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = Rating(editorRule.rule.data.first.toInt()).toStarRating().value,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {
        ruleDataChanged(
          editorRule,
          editorRule.rule.data.copy(first = StarRating(it).toRating().value.toLong())
        )
      },
      onRatingChanged = {},
    )
  }
}

@Composable
fun RatingRangeEditor(editorRule: EditorRule, ruleDataChanged: (EditorRule, MatcherData) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = Rating(editorRule.rule.data.first.toInt()).toStarRating().value,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {
        ruleDataChanged(
          editorRule,
          editorRule.rule.data.copy(first = StarRating(it).toRating().value.toLong())
        )
      },
      onRatingChanged = {},
    )
    Text(
      modifier = Modifier.padding(horizontal = 8.dp),
      text = stringResource(id = R.string.to),
      style = MaterialTheme.typography.caption
    )
    RatingBar(
      modifier = Modifier.wrapContentSize(),
      value = Rating(editorRule.rule.data.second.toInt()).toStarRating().value,
      size = 22.dp,
      padding = 2.dp,
      isIndicator = false,
      activeColor = LocalContentColor.current,
      inactiveColor = LocalContentColor.current,
      stepSize = StepSize.HALF,
      ratingBarStyle = RatingBarStyle.HighLighted,
      onValueChange = {
        ruleDataChanged(
          editorRule,
          editorRule.rule.data.copy(second = StarRating(it).toRating().value.toLong())
        )
      },
      onRatingChanged = {},
    )
  }
}

@Composable
fun EditTextRule(
  editorRule: EditorRule.TextEditorRule,
  ruleDataChanged: (EditorRule, MatcherData) -> Unit,
) {
  val rule = editorRule.rule
  val nameValidity = editorRule.nameValidity

  AutoCompleteTextView(
    modifier = Modifier.fillMaxWidth(),
    query = rule.data.text,
    suggestions = editorRule.suggestions,
    isError = nameValidity.isNotValid,
    onTextChanged = { text -> ruleDataChanged(editorRule, rule.data.copy(text = text)) },
    label = {
      Text(if (nameValidity.isNotValid) nameValidity.toString() else rule.ruleField.toString())
    },
    onDoneActionClick = {
      LOG._e { it("onDone") }
      ruleDataChanged(editorRule.copy(editing = false), rule.data)
    },
    isFocused = { isFocused ->
      LOG._e { it("isFocus:%s", isFocused) }
      ruleDataChanged(editorRule.copy(editing = isFocused), rule.data)
    }
  )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> RuleEditorDropdown(
  modifier: Modifier = Modifier,
  value: T,
  allValues: List<T>,
  valueChanged: (T) -> Unit,
) {
  val options = allValues.toList()
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    modifier = modifier,
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
  ) {
    CustomTextField(
      value = value.toString(),
      readOnly = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          onClick = {
            expanded = false
            valueChanged(option)
          }
        ) {
          Text(text = option.toString())
        }
      }
    }
  }
}

@Composable
private fun CustomTextField(
  modifier: Modifier = Modifier,
  value: String,
  readOnly: Boolean,
  enabled: Boolean = true,
  textStyle: TextStyle = LocalTextStyle.current,
  trailingIcon: @Composable (() -> Unit)? = null
) {
  val colors: TextFieldColors = TextFieldDefaults.textFieldColors()
  val backgroundColor = colors.backgroundColor(enabled).value

  BasicTextField(
    modifier = modifier
      .background(backgroundColor, MaterialTheme.shapes.small)
      .padding(start = 6.dp),
    value = value,
    readOnly = readOnly,
    onValueChange = {},
    maxLines = 1,
    singleLine = true,
    textStyle = textStyle.copy(color = LocalContentColor.current),
    decorationBox = { innerTextField ->
      Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.weight(1f)) { innerTextField() }
        if (trailingIcon != null) trailingIcon()
      }
    }
  )
}
