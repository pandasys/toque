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

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ealva.toque.R
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.ui.main.PermissionResult.Denied
import com.ealva.toque.ui.main.PermissionResult.Granted
import com.ealva.toque.ui.main.PermissionResult.NotRequested
import com.ealva.toque.ui.now.NowPlayingScreen
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestack.StateChange.REPLACE
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class GetReadExternalPermissionScreen(
  private val showRationale: Boolean = true
) : ComposeKey() {
  @Suppress("RemoveExplicitTypeArguments")
  override fun bindServices(serviceBinder: ServiceBinder) {
    super.bindServices(serviceBinder)
    with(serviceBinder) {
      add(GetReadExternalViewModel(showRationale, lookup(), backstack))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val goToSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    /*
     * Tried rememberLauncherForActivityResult but was getting "Launcher has not been initialized",
     * so keeping things in the ViewModel
     */
    val viewModel = rememberService<GetReadExternalViewModel>()
    val result: State<PermissionResult> = viewModel.result.collectAsState()

    val activity = LocalContext.current as Activity

    when (result.value) {
      NotRequested -> {
        if (viewModel.showRationale) {
          ShowRationale(onOk = { viewModel.requestPermission() })
        } else viewModel.requestPermission()
      }
      Denied -> {
        RepeatRationale(
          if (goToSettings) stringResource(R.string.SettingsRepeatRequiredToScanRationale) else
            stringResource(R.string.RepeatRequiredToScanRationale),
          if (goToSettings) stringResource(R.string.Settings) else
            stringResource(R.string.RequestPermission),
          onOk = { viewModel.retryPermission(activity, goToSettings) },
          onExit = { activity.finish() }
        )
      }
      Granted -> {
      }
    }
  }
}

/**
 * Note that we don't allow the dialog to be dismissed, including back press.
 */
@Composable
private fun ShowRationale(onOk: () -> Unit) {
  val showDialog = remember { mutableStateOf(true) }
  val closeDialog = { showDialog.value = false }
  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = {},
      title = { Text(text = stringResource(R.string.AccessExternalStorage)) },
      text = { Text(text = stringResource(R.string.RequiredToScanRationale)) },
      confirmButton = {
        TextButton(
          onClick = {
            closeDialog()
            onOk()
          },
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colors.secondary
          ),
        ) {
          Text(text = stringResource(R.string.OK))
        }
      }
    )
  }
}

/**
 * Note that we don't allow the dialog to be dismissed, including back press. User must choose
 * Exit or go to Settings
 */
@Composable
private fun RepeatRationale(
  dialogText: String,
  confirmButtonText: String,
  onOk: () -> Unit,
  onExit: () -> Unit
) {
  val showDialog = remember { mutableStateOf(true) }
  val closeDialog = { showDialog.value = false }
  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = { },
      title = { Text(text = stringResource(R.string.AccessExternalStorage)) },
      text = { Text(text = dialogText) },
      confirmButton = {
        TextButton(
          onClick = {
            closeDialog()
            onOk()
          },
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colors.secondary
          ),
        ) {
          Text(text = confirmButtonText)
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            closeDialog()
            onExit()
          },
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colors.secondary
          ),
        ) {
          Text(text = stringResource(R.string.Exit))
        }
      }
    )
  }
}

sealed interface PermissionResult {
  object Granted : PermissionResult
  object Denied : PermissionResult
  object NotRequested : PermissionResult
}

interface GetReadExternalViewModel {
  val result: StateFlow<PermissionResult>
  val showRationale: Boolean

  companion object {
    operator fun invoke(
      showRationale: Boolean,
      checkPermission: CheckPermission,
      backstack: Backstack
    ): GetReadExternalViewModel =
      GetReadExternalViewModelImpl(showRationale, checkPermission, backstack)
  }

  fun requestPermission()
  fun retryPermission(context: Context, goToSettings: Boolean)
}

private class GetReadExternalViewModelImpl(
  override var showRationale: Boolean,
  private val checkPermission: CheckPermission,
  private val backstack: Backstack
) : GetReadExternalViewModel, ScopedServices.Registered {
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  override val result = MutableStateFlow<PermissionResult>(NotRequested)

  override fun requestPermission() {
    scope.launch {
      showRationale = false
      if (checkPermission.requestPermission(READ_EXTERNAL_STORAGE)) {
        result.value = Granted
        backstack.setHistory(History.of(NowPlayingScreen()), REPLACE)
      } else {
        result.value = Denied
      }
    }
  }

  override fun retryPermission(context: Context, goToSettings: Boolean) {
    if (goToSettings) {
      context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
        }
      )
      result.value = NotRequested
    } else {
      result.value = NotRequested
    }
  }

  override fun onServiceRegistered() = Unit

  override fun onServiceUnregistered() = scope.cancel()
}
