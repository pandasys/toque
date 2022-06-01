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

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
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
import androidx.compose.runtime.State
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
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.theme.toque
import com.google.accompanist.insets.statusBarsPadding
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize

@Suppress("unused")
private val LOG by lazyLogger(SplashScreen::class)

@Immutable
@Parcelize
data class SplashScreen(private val noArg: String = "") : ComposeKey(), ScopeKey.Child {
  override fun getParentScopes(): List<String> = listOf(
    MainViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(SplashScreenModel(lookup()))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<SplashScreenModel>()

    Splash(
      viewModel.haveWritePermission.value,
      viewModel.getWriteExternalPermission,
      exit = { viewModel.exit() },
      goToSettings = { viewModel.startAppSettingsActivity() },
      gainedPermission = { viewModel.gainedReadExternalPermission() }
    )
  }
}

interface SplashScreenModel {
  val getWriteExternalPermission: Boolean
  val haveWritePermission: State<Boolean>

  fun exit()
  fun startAppSettingsActivity()
  fun gainedReadExternalPermission()

  companion object {
    operator fun invoke(
      mainModel: MainViewModel,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): SplashScreenModel = SplashScreenModelImpl(mainModel, dispatcher)
  }
}

private class SplashScreenModelImpl(
  private val mainModel: MainViewModel,
  dispatcher: CoroutineDispatcher,
) : SplashScreenModel, ScopedServices.Activated {
  private val scope = CoroutineScope(Job() + dispatcher)
  override val getWriteExternalPermission: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
  override val haveWritePermission = mutableStateOf(true)

  /**
   * Called when SplashScreen becomes active, either when the app starts or coming to the front
   * after an activity is shown for permission request, so check permissions here
   */
  override fun onServiceActive() {
    mainModel.haveWriteExternalPermission
      .onEach { havePerm -> haveWritePermissionChanged(havePerm) }
      .launchIn(scope)
  }

  private fun haveWritePermissionChanged(havePerm: Boolean) {
    if (havePerm) mainModel.gainedReadExternalPermission() else haveWritePermission.value = false
  }

  override fun onServiceInactive() {
    scope.cancel()
  }

  override fun exit() {
    mainModel.exit()
  }

  override fun startAppSettingsActivity() {
    mainModel.startAppSettingsActivity()
  }

  override fun gainedReadExternalPermission() {
    mainModel.gainedReadExternalPermission()
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Splash(
  haveWritePermission: Boolean,
  getWritePermission: Boolean,
  exit: () -> Unit,
  goToSettings: () -> Unit,
  gainedPermission: () -> Unit
) {
  var userExit by rememberSaveable { mutableStateOf(false) }
  val readExternalState = rememberPermissionState(WRITE_EXTERNAL_STORAGE)
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
    if (!haveWritePermission) {
      if (getWritePermission) {
        GetWritePermission(
          readExternalState = readExternalState,
          userExit = userExit,
          exit = exit,
          goToSettings = goToSettings,
          gainedPermission = gainedPermission,
          userSaysExit = { userExit = true },
          launchPermissionRequest = { readExternalState.launchPermissionRequest() }
        )
      } else {
        Rationale(userSaysExit = exit, onRequestPermission = goToSettings)
      }
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun GetWritePermission(
  readExternalState: PermissionState,
  userExit: Boolean,
  exit: () -> Unit,
  goToSettings: () -> Unit,
  gainedPermission: () -> Unit,
  userSaysExit: () -> Unit,
  launchPermissionRequest: () -> Unit
) {
  PermissionRequired(
    permissionState = readExternalState,
    permissionNotGrantedContent = {
      PermissionNotGranted(
        userExit = userExit,
        exit = exit,
        launchPermissionRequest = launchPermissionRequest,
        userSaysExit = userSaysExit
      )
    },
    permissionNotAvailableContent = {
      PermissionNotAvailable(
        userExit = userExit,
        exit = exit,
        goToSettings = goToSettings,
        userSaysExit = userSaysExit
      )
    },
    content = { gainedPermission() }
  )
}

@Composable
fun PermissionNotAvailable(
  userExit: Boolean,
  exit: () -> Unit,
  goToSettings: () -> Unit,
  userSaysExit: () -> Unit
) = if (userExit) exit() else PermissionDenied(userSaysExit, goToSettings)

@Composable
fun PermissionNotGranted(
  userExit: Boolean,
  exit: () -> Unit,
  launchPermissionRequest: () -> Unit,
  userSaysExit: () -> Unit
) = if (userExit) exit() else Rationale(userSaysExit, launchPermissionRequest)

@Composable
private fun Rationale(
  userSaysExit: () -> Unit,
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
      Button(onClick = userSaysExit) {
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
