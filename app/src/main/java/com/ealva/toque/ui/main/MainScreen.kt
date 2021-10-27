/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.now.NowPlayingScreen
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
  composeStateChanger: ComposeStateChanger,
  topOfStack: ComposeKey,
  goToSettings: () -> Unit,
  goToNowPlaying: () -> Unit,
  goToLibrary: () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxSize()) {

    val scaffoldState = rememberScaffoldState()
    val (isBottomSheetExpanded, expandBottomSheet) = remember { mutableStateOf(false) }
    expandBottomSheet(topOfStack !is SplashScreen && topOfStack !is NowPlayingScreen)

    val screenConfig = LocalScreenConfig.current

    val bottomBarHeightPx = remember { mutableStateOf(0f) }
    val bottomBarOffsetHeightPx = remember { mutableStateOf(0f) }

    val bottomBarHeight = screenConfig.getNavPlusBottomSheetHeight(isBottomSheetExpanded)

    bottomBarHeightPx.value = with(screenConfig) { bottomBarHeight.roundToPx().toFloat() }
    bottomBarOffsetHeightPx.value = 0f // if topOfStack changes we recompose, reset bottom sheet

    val nestedScrollConnection = remember {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          bottomBarOffsetHeightPx.value = (bottomBarOffsetHeightPx.value + available.y)
            .coerceIn(-bottomBarHeightPx.value, 0f)
          return Offset.Zero
        }
      }
    }

    ProvideSnackbarHostState(snackbarHostState = scaffoldState.snackbarHostState) {
      if (topOfStack is SplashScreen) {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
          scaffoldState = scaffoldState,
        ) {
          composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
        }
      } else {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
          scaffoldState = scaffoldState,
          bottomBar = {
            MainBottomSheet(
              topOfStack = topOfStack,
              isExpanded = isBottomSheetExpanded,
              goToSettings = goToSettings,
              goToNowPlaying = goToNowPlaying,
              goToLibrary = goToLibrary,
              config = screenConfig,
              modifier = Modifier
                .fillMaxWidth()
                .height(bottomBarHeight)
                .navigationBarsPadding(bottom = false)
                .padding(horizontal = 12.dp)
                .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.value.roundToInt()) }
            )
          },
          backgroundColor = Color.Transparent,
        ) {
          composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
        }
      }
    }
  }
}
