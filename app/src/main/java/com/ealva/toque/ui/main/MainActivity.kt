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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.library.EqPresetEditorScreen
import com.ealva.toque.ui.library.LibraryCategoriesScreen
import com.ealva.toque.ui.library.SearchScreen
import com.ealva.toque.ui.nav.goToAboveRoot
import com.ealva.toque.ui.nav.goToScreen
import com.ealva.toque.ui.nav.jumpToRootScreen
import com.ealva.toque.ui.queue.QueueScreen
import com.ealva.toque.ui.settings.AppSettingsScreen
import com.ealva.toque.ui.settings.SettingScreenKeys.PrimarySettings
import com.ealva.toque.ui.theme.ToqueTheme
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.zhuinden.simplestack.AsyncStateChanger
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@Suppress("unused")
private val LOG by lazyLogger(MainActivity::class)

private val topOfStackFlow = MutableStateFlow<ComposeKey>(SplashScreen())

private val KEY_BACKSTACK = "${MainActivity::class.java.name}_BACK_STACK"

interface MainBridge {
  val haveReadExternalPermission: Boolean
  val activityContext: Context

  fun startAppSettingsActivity()
  fun exit()
  val canDrawOverlays: Boolean
  fun requestOverlayPermission()
}

class MainActivity : ComponentActivity(), MainBridge {
  private lateinit var scope: CoroutineScope
  private val composeStateChanger = makeAppComposeStateChanger()
  private lateinit var backstack: Backstack
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)

  private val overlayLauncher = registerForActivityResult(StartActivityForResult()) {
    scope.launch { appPrefsSingleton.instance().showLockScreenPlayer.set(canDrawOverlays) }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    backstack = Navigator.configure()
      .addStateChangeCompletionListener { topOfStackFlow.value = it.topNewKey() }
      .setGlobalServices(ToqueGlobalServicesFactory(this, appPrefsSingleton))
      .setScopedServices(ServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, makeInitialHistory())

    setContent {
      BackstackProvider(backstack) {
        val themeViewModel = rememberService<ThemeViewModel>()
        val themeChoice by themeViewModel.themeChoice.collectAsState()

        val topOfStackState by topOfStackFlow.collectAsState()

        ToqueTheme(themeChoice) {
          ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
            ProvideScreenConfig(
              screenConfig = makeScreenConfig(
                LocalConfiguration.current,
                LocalDensity.current,
                LocalWindowInsets.current
              )
            ) {
              MainScreen(
                composeStateChanger = composeStateChanger,
                topOfStack = topOfStackState,
                goToNowPlaying = { backstack.jumpToRootScreen() },
                goToLibrary = { backstack.goToAboveRoot(LibraryCategoriesScreen()) },
                goToQueue = { backstack.goToAboveRoot(QueueScreen()) },
                goToSearch = { backstack.goToAboveRoot(SearchScreen()) },
                goToPresetEditor = { backstack.goToAboveRoot(EqPresetEditorScreen()) },
                goToSettings = { backstack.goToScreen(AppSettingsScreen(PrimarySettings)) }
              )
            }
          }
        }
      }
    }
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    super.onRestoreInstanceState(savedInstanceState)
    savedInstanceState.getParcelable<StateBundle>(KEY_BACKSTACK)?.let { stateBundle ->
      backstack.fromBundle(stateBundle)
      // SplashScreen typically requests read external permission, but if we have a backstack
      // and SplashScreen is not at the top, we should have the permission and need to behave as
      // such
      if (backstack.top<ComposeKey>() !is SplashScreen) {
        if (checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
          backstack.lookup<MainViewModel>().gainedReadExternalPermission()
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putParcelable(KEY_BACKSTACK, backstack.toBundle())
    super.onSaveInstanceState(outState)
  }

  override val haveReadExternalPermission: Boolean
    get() = checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED


  override val activityContext: Context
    get() = this

  override fun startAppSettingsActivity() {
    startActivity(
      Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
      )
    )
  }

  override fun exit() {
    finishAfterTransition()
  }

  override val canDrawOverlays: Boolean
    get() = Settings.canDrawOverlays(this)

  override fun requestOverlayPermission() {
    overlayLauncher.launch(
      Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        intent.data = Uri.fromParts("package", packageName, null)
      }
    )
  }

  override fun onBackPressed() {
    if (!Navigator.onBackPressed(this)) super.onBackPressed()
  }

  private fun makeInitialHistory(): History<ComposeKey> = History.of(SplashScreen())
}

private fun makeAppComposeStateChanger() = ComposeStateChanger(
  ComposeStateChanger.AnimationConfiguration(
    previousComposableTransition = { modifier, _, _, _, animationProgress ->
      modifier.then(Modifier.graphicsLayer(alpha = (1 - animationProgress)))
    },
    newComposableTransition = { modifier, _, _, _, animationProgress ->
      modifier.then(Modifier.graphicsLayer(alpha = (animationProgress)))
    }
  )
)
