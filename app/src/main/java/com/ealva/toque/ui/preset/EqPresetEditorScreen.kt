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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.ui.theme.toqueTypography
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.annotation.concurrent.Immutable


@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorScreen::class)

@Immutable
@Parcelize
data class EqPresetEditorScreen(private val noArg: String = "") : ComposeKey(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(EqPresetEditorModel(backstack, get()))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<EqPresetEditorModel>()
    val state = viewModel.editorState.collectAsState()

    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false),
      topBar = {
        EqPresetEditorTopBar(
          preset = state.value.currentPreset,
          goBack = { viewModel.goBack() }
        )
      }
    ) {

    }
  }
}

private val CHART_WIDTH = 120.dp
private val CHART_HEIGHT = 40.dp
private val PADDING_START = 0.dp
private val PADDING_TOP = 0.dp
private val PADDING_END = 0.dp
private val PADDING_BOTTOM = 4.dp
private val PADDING = PaddingValues(
  start = PADDING_START,
  top = PADDING_TOP,
  end = PADDING_END,
  bottom = PADDING_BOTTOM
)

@Composable
private fun EqPresetEditorTopBar(
  preset: EqPreset,
  goBack: () -> Unit
) {
  TopAppBar(
    title = {
      Box(
        modifier = Modifier
          .width(CHART_WIDTH)
          .height(CHART_HEIGHT)
      ) {
        EqPresetLineChart(
          preset = preset,
          width = CHART_WIDTH,
          height = CHART_HEIGHT,
          padding = PADDING
        )
        Text(
          modifier = Modifier.align(Alignment.BottomEnd),
          text = preset.displayName,
          style = toqueTypography.caption
        )
      }
    },
    navigationIcon = {
      IconButton(onClick = goBack) {
        Icon(
          painter = painterResource(id = R.drawable.ic_navigate_before),
          contentDescription = "Back",
          modifier = Modifier.size(26.dp)
        )
      }
    }
  )
}
