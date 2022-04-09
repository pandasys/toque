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

package com.ealva.toque.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Surface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ealva.toque.R
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.common.SwipeableSnackbarHost
import com.ealva.toque.ui.main.MainViewModel
import com.ealva.toque.ui.main.ProvideSnackbarHostState
import com.ealva.toque.ui.theme.toqueColors
import com.google.accompanist.insets.navigationBarsPadding
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.services.rememberService

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainLockScreen(
  composeStateChanger: ComposeStateChanger,
  unlock: () -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color.Transparent
  ) {
    val mainModel: MainViewModel = rememberService()

    mainModel.bindIfHaveReadExternalPermission()

    val scaffoldState = rememberScaffoldState()
    val snackbarHostState = scaffoldState.snackbarHostState

    val dialogPrompt by mainModel.promptFlow.collectAsState()
    val screenConfig = LocalScreenConfig.current
    val bottomBarHeight = screenConfig.getBottomSheetButtonBarHeight()

    val inPortrait = screenConfig.inPortrait
    ProvideSnackbarHostState(snackbarHostState = scaffoldState.snackbarHostState) {
      Scaffold(
        modifier = Modifier
          .fillMaxSize(),
        scaffoldState = scaffoldState,
        snackbarHost = { SwipeableSnackbarHost(hostState = it) },
        bottomBar = {
          LockScreenBottomSheet(
            unlock = unlock,
            modifier = Modifier
              .fillMaxWidth()
              .height(bottomBarHeight)
              .navigationBarsPadding(bottom = false)
              .padding(horizontal = if (inPortrait) 12.dp else 40.dp)
              .offset { IntOffset(x = 0, y = if (inPortrait) -bottomBarHeight.roundToPx() else 0) }
          )
        },
      ) {
        composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
      }

      dialogPrompt.prompt?.let { showDialog -> showDialog() }

      LaunchedEffect(Unit) {
        mainModel.notificationFlow
          .collect { notification ->
            when (
              snackbarHostState.showSnackbar(
                message = notification.msg,
                actionLabel = notification.action.label,
                duration = notification.duration
              )
            ) {
              SnackbarResult.ActionPerformed -> notification.action.action()
              SnackbarResult.Dismissed -> notification.action.expired()
            }
          }
      }
    }
  }
}

@Composable
fun LockScreenBottomSheet(
  modifier: Modifier = Modifier,
  unlock: () -> Unit
) {
  val imageSize = LocalScreenConfig.current.getBottomSheetButtonBarHeight() - 12.dp

  Box(
    modifier = modifier
  ) {
    Card(
      shape = RoundedCornerShape(20),
      elevation = if (toqueColors.isLight) 4.dp else 1.dp
    ) {
      Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        IconButton(
          onClick = unlock,
          modifier = Modifier.weight(1.0F)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_outline_lock_open_24),
            contentDescription = "Library",
            modifier = Modifier.size(imageSize),
            tint = LocalContentColor.current
          )
        }
      }
    }
  }
}
