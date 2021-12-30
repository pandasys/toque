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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.ThemeChoice
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
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
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val topOfStackFlow = MutableStateFlow<ComposeKey>(SplashScreen())

private val KEY_BACKSTACK = "${MainActivity::class.java.name}_BACK_STACK"

interface MainBridge {
  fun startAppSettingsActivity()
  fun exit()
}

class MainActivity : ComponentActivity(), MainBridge {
  private lateinit var scope: CoroutineScope
  private val composeStateChanger = makeAppComposeStateChanger()
  private lateinit var backstack: Backstack
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    backstack = Navigator.configure()
      .addStateChangeCompletionListener { topOfStackFlow.value = it.topNewKey() }
      .setGlobalServices(ToqueGlobalServicesFactory(this, appPrefsSingleton))
      .setScopedServices(DefaultServiceProvider())
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
          checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
          backstack.lookup<MainViewModel>().gainedReadExternalPermission()
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putParcelable(KEY_BACKSTACK, backstack.toBundle())
    super.onSaveInstanceState(outState)
  }

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

interface ThemeViewModel {
  val themeChoice: StateFlow<ThemeChoice>

  companion object {
    operator fun invoke(appPrefsSingleton: AppPrefsSingleton): ThemeViewModel =
      ThemeViewModelImpl(appPrefsSingleton)
  }
}

private class ThemeViewModelImpl(
  private val appPrefsSingleton: AppPrefsSingleton,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ThemeViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val themeChoice = MutableStateFlow(ThemeChoice.System)

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    scope.launch {
      appPrefsSingleton.instance()
        .themeChoice
        .asFlow()
        .onEach { themeChoice.value = it }
        .launchIn(scope)
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
  }
}

class ToqueGlobalServicesFactory(
  private val mainBridge: MainBridge,
  private val appPrefsSingleton: AppPrefsSingleton,
) : GlobalServices.Factory {
  override fun create(backstack: Backstack): GlobalServices = GlobalServices.builder().apply {
    val mainViewModel = MainViewModel(mainBridge, backstack)
    add(mainViewModel)
    add(LocalAudioMiniPlayerViewModel(mainViewModel))
    add(ThemeViewModel(appPrefsSingleton))
  }.build()
}
