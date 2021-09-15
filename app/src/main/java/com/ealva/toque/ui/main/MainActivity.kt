/*
 * Copyright 2020 eAlva.com
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
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.ui.now.NowPlayingScreen
import com.ealva.toque.ui.theme.ToqueTheme
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.zhuinden.simplestack.AsyncStateChanger
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.StateChange.REPLACE
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.navigatorktx.backstack
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

private val LOG by lazyLogger(MainActivity::class)

class MainActivity : AppCompatActivity() {
  private val composeStateChanger = ComposeStateChanger()
  private var haveReadExternalPermission = false
  private val playerServiceConnection = MediaPlayerServiceConnection(this)
  private val appPrefsSingleton: AppPrefsSingleton by inject(named("AppPrefs"))
  private val audioMediaDao: AudioMediaDao by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      when (val result = audioMediaDao.getCountAllAudio()) {
        is Ok -> LOG.i { it("Audio count=%d", result.value) }
        is Err -> LOG.e { it("Failed to get count of all audio") }
      }
    }

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets
    haveReadExternalPermission = havePermission(READ_EXTERNAL_STORAGE)

    val backstack = Navigator.configure()
      .setGlobalServices(getGlobalServicesBuilder().build())
      .setScopedServices(DefaultServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, makeInitialHistory(haveReadExternalPermission))

    setContent {
      BackstackProvider(backstack) {
        ToqueTheme {
          Box(Modifier.fillMaxSize()) {
            composeStateChanger.RenderScreen()
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // If user goes to settings and enables permission we'll react to that here
    if (!haveReadExternalPermission) {
      if (havePermission(READ_EXTERNAL_STORAGE)) {
        playerServiceConnection.bind()
        haveReadExternalPermission = true
        backstack.setHistory(History.of(NowPlayingScreen()), REPLACE)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    playerServiceConnection.unbind()
  }

  private fun getGlobalServicesBuilder(): GlobalServices.Builder = globalServices.apply {
    if (doNotHavePermission(READ_EXTERNAL_STORAGE)) {
      add(CheckPermission(this@MainActivity))
    }
    add(appPrefsSingleton)
    add(playerServiceConnection)
  }

  private val globalServices get() = (application as Toque).globalServicesBuilder

  override fun onBackPressed() {
    if (!Navigator.onBackPressed(this)) {
      super.onBackPressed()
    }
  }

  private fun makeInitialHistory(
    haveReadExternalPermission: Boolean
  ): History<ComposeKey> = if (haveReadExternalPermission) {
    History.of(NowPlayingScreen())
  } else {
    History.of(GetReadExternalPermissionScreen(showRationale()))
  }

  private fun showRationale(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    shouldShowRequestPermissionRationale(READ_EXTERNAL_STORAGE)
  } else {
    false
  }

  @Suppress("SameParameterValue")
  private fun havePermission(
    permission: String
  ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    checkSelfPermission(permission) == PERMISSION_GRANTED
  } else true

  @Suppress("SameParameterValue")
  private fun doNotHavePermission(permission: String) = !havePermission(permission)
}
