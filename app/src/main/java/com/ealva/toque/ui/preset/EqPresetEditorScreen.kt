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

package com.ealva.toque.ui.preset

import android.content.pm.ActivityInfo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutBaseScope
import androidx.constraintlayout.compose.ConstraintLayoutScope
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqBand
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.AssignButton
import com.ealva.toque.ui.common.BackButton
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.PopupMenu
import com.ealva.toque.ui.common.PopupMenuItem
import com.ealva.toque.ui.main.LockScreenOrientation
import com.ealva.toque.ui.preset.EqPresetEditorModel.Preset
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable
import kotlin.math.roundToInt


private val Amp.asDbString: String
  get() = "%.1f dB".format((value * 10F).roundToInt() / 10F)

private val ClosedRange<Amp>.asFloatRange: ClosedFloatingPointRange<Float>
  get() = start.value..endInclusive.value

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorScreen::class)

@Immutable
@Parcelize
data class EqPresetEditorScreen(
  private val noArg: String = ""
) : ComposeKey(), ScopeKey.Child, KoinComponent {
  @IgnoredOnParcel
  private lateinit var viewModel: EqPresetEditorModel

  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      viewModel = EqPresetEditorModel(lookup(), lookup(), backstack, get())
      add(viewModel)
    }
  }

  override fun navigateIfAllowed(command: () -> Unit) {
    viewModel.navigateIfAllowed(command)
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<EqPresetEditorModel>()
    LockScreenOrientation(
      requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      screenOrientation = viewModel
    )
    val state = viewModel.editorState.collectAsState()
    EqScreenEditor(state.value, viewModel)
  }
}

@Composable
private fun EqScreenEditor(state: EqPresetEditorModel.State, viewModel: EqPresetEditorModel) {
  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false),
    topBar = {
      EqPresetEditorTopBar(
        current = state.currentPreset,
        all = state.allPresets,
        menuItems = state.makeMenuItems(viewModel),
        assignPreset = { viewModel.assignPreset() },
        selectionChanged = { preset -> viewModel.presetSelected(preset) },
        goBack = { viewModel.goBack() }
      )
    }
  ) {
    EqBandsEditor(
      currentPreset = state.currentPreset,
      preAmpChanged = { amp -> viewModel.setPreAmp(amp) },
      bandChanged = { band, amp -> viewModel.setBand(band, amp) }
    )
  }
}

@Composable
fun EqBandsEditor(
  currentPreset: Preset,
  preAmpChanged: (Amp) -> Unit,
  bandChanged: (EqBand, Amp) -> Unit
) {
  val scrollState = rememberScrollState()
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 6.dp, end = 18.dp)
      .scrollable(state = scrollState, orientation = Orientation.Vertical)
  ) {
    ConstraintLayout(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 6.dp, end = 18.dp)
    ) {
      val bandValueRefs = buildList { repeat(currentPreset.eqBands.size + 1) { add(createRef()) } }
      val bandEditorRefs = buildList { repeat(currentPreset.eqBands.size + 1) { add(createRef()) } }
      val barrier = createEndBarrier(elements = bandValueRefs.toTypedArray())

      EqBandEditor(
        bandValueRef = bandValueRefs[0],
        bandEditorRef = bandEditorRefs[0],
        eqBand = EqBand.PreAmp,
        ampValue = currentPreset.bandData.preAmp,
        isEditable = currentPreset.isEditable,
        barrier = barrier,
        editorTopLinkTo = null,
        bandChanged = { _, amp -> preAmpChanged(amp) }
      )
      currentPreset.eqBands.forEachIndexed { bandIndex, eqBand ->
        val index = bandIndex + 1
        EqBandEditor(
          bandValueRefs[index],
          bandEditorRefs[index],
          eqBand,
          currentPreset.bandData[eqBand],
          currentPreset.isEditable,
          barrier,
          bandEditorRefs[index - 1].bottom,
          bandChanged
        )
      }
    }
  }
}

@Composable
private fun ConstraintLayoutScope.EqBandEditor(
  bandValueRef: ConstrainedLayoutReference,
  bandEditorRef: ConstrainedLayoutReference,
  eqBand: EqBand,
  ampValue: Amp,
  isEditable: Boolean,
  barrier: ConstraintLayoutBaseScope.VerticalAnchor,
  editorTopLinkTo: ConstraintLayoutBaseScope.HorizontalAnchor?,
  bandChanged: (EqBand, Amp) -> Unit
) {
  BandValueAndFrequency(
    amp = ampValue,
    eqBand = eqBand,
    modifier = Modifier.Companion
      .constrainAs(bandValueRef) {
        start.linkTo(parent.start)
        top.linkTo(bandEditorRef.top)
        end.linkTo(barrier)
        bottom.linkTo(bandEditorRef.bottom)
      }
  )
  BandValueEditor(
    amp = ampValue,
    valueChanged = { amp -> bandChanged(eqBand, amp) },
    enabled = isEditable,
    modifier = Modifier.Companion
      .constrainAs(bandEditorRef) {
        start.linkTo(barrier)
        top.linkTo(editorTopLinkTo ?: parent.top)
        end.linkTo(parent.end)
      }
  )
}

@Composable
private fun BandValueEditor(
  modifier: Modifier = Modifier,
  amp: Amp,
  valueChanged: (Amp) -> Unit,
  enabled: Boolean
) {
  Slider(
    modifier = modifier.padding(start = 8.dp),
    value = amp.value,
    onValueChange = { value -> valueChanged(Amp(value)) },
    enabled = enabled,
    valueRange = Amp.RANGE.asFloatRange
  )
}

@Composable
private fun BandValueAndFrequency(
  modifier: Modifier = Modifier,
  amp: Amp,
  eqBand: EqBand
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = amp.asDbString,
      style = toqueTypography.caption
    )
    Text(
      text = eqBand.title,
      style = toqueTypography.caption
    )
  }
}

private val PORTRAIT_CHART_WIDTH = 140.dp
private val LANDSCAPE_CHART_WIDTH = 200.dp
private val SELECTED_CHART_HEIGHT = 40.dp
private val PORTRAIT_MENU_WIDTH = 70.dp
private val LANDSCAPE_MENU_WIDTH = 90.dp
private val MENU_CHART_HEIGHT = 40.dp

@Composable
private fun EqPresetEditorTopBar(
  current: Preset,
  all: List<Preset>,
  menuItems: List<PopupMenuItem>,
  assignPreset: () -> Unit,
  selectionChanged: (Preset) -> Unit,
  goBack: () -> Unit
) {
  TopAppBar(
    title = {
      PresetDropdownMenu(selected = current, allPresets = all, selectionChanged = selectionChanged)
    },
    navigationIcon = { BackButton(goBack) },
    actions = {
      AssignButton(onClick = assignPreset)
      PopupMenu(items = menuItems)
    }
  )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun PresetDropdownMenu(
  modifier: Modifier = Modifier,
  selected: Preset,
  allPresets: List<Preset>,
  selectionChanged: (Preset) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  val screenConfig = LocalScreenConfig.current
  val chartWidth = if (screenConfig.inPortrait) PORTRAIT_CHART_WIDTH else LANDSCAPE_CHART_WIDTH
  val menuWidth = if (screenConfig.inPortrait) PORTRAIT_MENU_WIDTH else LANDSCAPE_MENU_WIDTH

  ExposedDropdownMenuBox(
    modifier = modifier,
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
  ) {
    Row(
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically
    ) {
      ChartNameBox(selected, chartWidth)
//      ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
    }
    ExposedDropdownMenu(
      modifier = Modifier.width(menuWidth + 160.dp),
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      allPresets.forEach { preset ->
        DropdownMenuItem(
          onClick = { expanded = false; selectionChanged(preset) },
          content = { LabeledChart(selected = preset, menuWidth) }
        )
      }
    }
  }
}

@Composable
private fun ChartNameBox(selected: Preset, width: Dp) {
  Box(
    modifier = Modifier
      .width(width)
      .height(SELECTED_CHART_HEIGHT)
  ) {
    EqPresetLineChart(
      bands = selected.bandData.bandValues,
      width = width,
      height = SELECTED_CHART_HEIGHT
    )
    Text(
      modifier = Modifier.align(Alignment.BottomEnd),
      text = selected.displayName,
      style = toqueTypography.caption
    )
  }
}

@Composable
private fun LabeledChart(selected: Preset, width: Dp) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    EqPresetLineChart(
      bands = selected.bandData.bandValues,
      width = width,
      height = MENU_CHART_HEIGHT
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = selected.displayName,
      style = toqueTypography.caption
    )
  }
}
