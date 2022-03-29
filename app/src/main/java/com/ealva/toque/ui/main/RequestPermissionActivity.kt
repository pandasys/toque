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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.app.Toque
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.main.RequestPermissionActivity.Companion.PermissionData
import com.ealva.toque.ui.theme.ToqueTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
import com.zhuinden.simplestack.AsyncStateChanger
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineScope
import kotlinx.parcelize.Parcelize
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(RequestPermissionActivity::class)

class RequestPermissionActivity : ComponentActivity() {
  private lateinit var scope: CoroutineScope
  private lateinit var backstack: Backstack
  private val composeStateChanger = ComposeStateChanger()
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)

  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    scope = lifecycleScope

    val data: PermissionData = intent.getParcelableExtra(KEY_PERMISSION_DATA) ?: PermissionData.NONE

    if (data === PermissionData.NONE) {
      LOG.e { it("No PermissionData") }
      finish()
    }

    backstack = Navigator.configure()
      .setGlobalServices(GlobalServicesFactory(appPrefsSingleton))
      .setScopedServices(ServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, History.of(SplashScreen()))

    setContent {
      BackstackProvider(backstack) {
        val themeViewModel = rememberService<ThemeViewModel>()
        val themeChoice by themeViewModel.themeChoice.collectAsState()

        ToqueTheme(themeChoice) {
          var userExit by rememberSaveable { mutableStateOf(false) }
          val permissionState = rememberPermissionState(data.permissionString)

          if (userExit) finishAfterTransition() else {
            Column(
              modifier = Modifier
                .fillMaxSize()
            ) {
              TopAppBar(title = {
                Text(text = stringResource(id = data.windowTitle))
              })
              PermissionRequired(
                permissionState = permissionState,
                permissionNotGrantedContent = {
                  Rationale(
                    data = data,
                    userExit = { userExit = true },
                    onRequestPermission = { permissionState.launchPermissionRequest() }
                  )
                },
                permissionNotAvailableContent = {
                  PermissionDenied(
                    data = data,
                    userExit = { userExit = true },
                    navigateToSettingsScreen = {
                      startActivity(
                        Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.fromParts("package", packageName, null)
                        )
                      )
                    }
                  )
                }
              ) {
                finishAfterTransition()
              }
            }
          }
        }
      }
    }
  }

  companion object {
    private const val KEY_PERMISSION_DATA = "PermissionData"

    @Parcelize
    data class PermissionData(
      val permissionString: String,
      @StringRes val windowTitle: Int,
      @StringRes val rationale: Int,
      @StringRes val goToSettingRationale: Int,
    ) : Parcelable {
      companion object {
        val NONE = PermissionData(
          "none",
          R.string.None,
          R.string.None,
          R.string.None
        )
      }
    }

    val READ_PHONE_STATE_DATA = PermissionData(
      permissionString = android.Manifest.permission.READ_PHONE_STATE,
      windowTitle = R.string.ReadPhoneIdleState,
      rationale = R.string.ReadPhoneStateRationale,
      goToSettingRationale = R.string.ReadPhoneStateSettingsRationale
    )

    fun start(
      permissionData: PermissionData
    ) = Toque.appContext.run {
      val intent = Intent(this, RequestPermissionActivity::class.java).apply {
        putExtra(KEY_PERMISSION_DATA, permissionData)
        flags = FLAG_ACTIVITY_NEW_TASK
      }
      startActivity(intent)
    }
  }
}

@Composable
private fun Rationale(
  data: PermissionData,
  userExit: () -> Unit,
  onRequestPermission: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp, vertical = 12.dp)
  ) {
    Text(
      text = stringResource(id = data.rationale),
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
  data: PermissionData,
  userExit: () -> Unit,
  navigateToSettingsScreen: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 18.dp, vertical = 12.dp)
  ) {
    Text(text = stringResource(id = data.goToSettingRationale), color = Color.White)
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

private class GlobalServicesFactory(
  private val appPrefsSingleton: AppPrefsSingleton,
) : GlobalServices.Factory {
  override fun create(backstack: Backstack): GlobalServices = GlobalServices.builder().apply {
    add(ThemeViewModel(appPrefsSingleton))
  }.build()
}
