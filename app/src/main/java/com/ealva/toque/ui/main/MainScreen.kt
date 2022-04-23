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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.SwipeableSnackbarHost
import com.ealva.toque.ui.now.NowPlayingScreen
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

@Suppress("unused")
private val LOG by lazyLogger("MainScreen")

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
  composeStateChanger: ComposeStateChanger,
  topOfStack: ComposeKey,
  goToNowPlaying: () -> Unit,
  goToLibrary: () -> Unit,
  goToQueue: () -> Unit,
  goToSearch: () -> Unit,
  goToPresetEditor: () -> Unit,
  goToSettings: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.Transparent
  ) {
    val mainModel: MainViewModel = rememberService()

    val scaffoldState = rememberScaffoldState()
    val snackbarHostState = scaffoldState.snackbarHostState
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

    val dialogPrompt by mainModel.promptFlow.collectAsState()

    ProvideSnackbarHostState(snackbarHostState = scaffoldState.snackbarHostState) {
      if (topOfStack is SplashScreen) {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
          scaffoldState = scaffoldState,
          snackbarHost = { SwipeableSnackbarHost(hostState = it) }
        ) {
          composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
        }
      } else {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
          scaffoldState = scaffoldState,
          snackbarHost = { SwipeableSnackbarHost(hostState = it) },
          bottomBar = {
            MainBottomSheet(
              topOfStack = topOfStack,
              isExpanded = isBottomSheetExpanded,
              goToNowPlaying = goToNowPlaying,
              goToLibrary = goToLibrary,
              goToQueue = goToQueue,
              goToSearch = goToSearch,
              goToPresetEditor = goToPresetEditor,
              goToSettings = goToSettings,
              modifier = Modifier
                .fillMaxWidth()
                .height(bottomBarHeight)
                .navigationBarsPadding(bottom = false)
                .padding(horizontal = if (screenConfig.inPortrait) 12.dp else 40.dp)
                .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.value.roundToInt()) }
            )
          },
        ) {
          composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
        }

        dialogPrompt.prompt?.let { showDialog -> showDialog() }

        LaunchedEffect(Unit) {
          mainModel.notificationFlow
            .onEach { notification -> notification.showSnackbar(snackbarHostState) }
            .catch { cause -> LOG.e(cause) { it("NotificationFlow error") } }
            .onCompletion { LOG._i { it("NotificationFlow complete") } }
            .launchIn(this)
        }
      }
    }
  }
}
