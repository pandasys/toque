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

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.theme.toque
import com.google.accompanist.insets.statusBarsPadding
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class SplashScreen(private val noArg: String = "") : ComposeKey() {
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val mainViewModel = rememberService<MainViewModel>()
    Splash(
      exit = { mainViewModel.exit() },
      navigateToSettingsScreen = { mainViewModel.startAppSettingsActivity() },
      gainedPermission = { mainViewModel.gainedReadExternalPermission() }
    )
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Splash(
  exit: () -> Unit,
  navigateToSettingsScreen: () -> Unit,
  gainedPermission: () -> Unit
) {
  var userExit by rememberSaveable { mutableStateOf(false) }
  val readExternalState = rememberPermissionState(READ_EXTERNAL_STORAGE)
  val imageSize = if (LocalScreenConfig.current.inPortrait) 200.dp else 120.dp

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 20.dp, end = 20.dp)
      .statusBarsPadding()
  ) {
    Image(
      painter = painterResource(R.drawable.ic_toque),
      modifier = Modifier
        .align(alignment = Alignment.CenterHorizontally)
        .size(imageSize, imageSize),
      contentDescription = "Toque",
    )
    Text(
      text = stringResource(id = R.string.app_name),
      fontSize = 24.sp,
      fontWeight = FontWeight.SemiBold,
      color = toque,
      modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(20.dp))
    PermissionRequired(
      permissionState = readExternalState,
      permissionNotGrantedContent = {
        if (userExit) {
          exit()
        } else {
          Rationale(
            userExit = { userExit = true },
            onRequestPermission = { readExternalState.launchPermissionRequest() }
          )
        }
      },
      permissionNotAvailableContent = {
        if (userExit) {
          exit()
        } else {
          PermissionDenied(
            userExit = { userExit = true },
            navigateToSettingsScreen
          )
        }
      }
    ) {
      gainedPermission()
    }
  }
}

@Composable
private fun Rationale(
  userExit: () -> Unit,
  onRequestPermission: () -> Unit
) {
  Column {
    Text(
      text = stringResource(id = R.string.RequiredToScanRationale),
      color = Color.White
    )
    Row(
      modifier = Modifier
        .padding(top = 12.dp)
        .align(Alignment.CenterHorizontally)
    ) {
      Button(onClick = onRequestPermission) {
        Text(text = stringResource(id = R.string.RequestPermission))
      }
      Spacer(Modifier.width(8.dp))
      Button(onClick = userExit) {
        Text(stringResource(id = R.string.Exit))
      }
    }
  }
}

@Composable
private fun PermissionDenied(
  userExit: () -> Unit,
  navigateToSettingsScreen: () -> Unit
) {
  Column {
    Text(text = stringResource(id = R.string.SettingsScanRationale), color = Color.White)
    Row(
      modifier = Modifier
        .padding(top = 12.dp)
        .align(Alignment.CenterHorizontally)
    ) {
      Button(onClick = navigateToSettingsScreen) {
        Text(stringResource(R.string.OpenSettings))
      }
      Spacer(Modifier.width(8.dp))
      Button(onClick = userExit) {
        Text(stringResource(id = R.string.Exit))
      }
    }
  }
}
