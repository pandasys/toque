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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.android.content.haveReadPermission
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.ealva.toque.ui.config.ProvideScreenConfig
import com.ealva.toque.ui.config.makeScreenConfig
import com.ealva.toque.ui.library.LibraryCategoriesScreen
import com.ealva.toque.ui.library.SearchScreen
import com.ealva.toque.ui.nav.goToAboveRoot
import com.ealva.toque.ui.now.NowPlayingScreen
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
import com.zhuinden.simplestack.StateChange.REPLACE
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(MainActivity::class)

private val topOfStackState: MutableState<ComposeKey> = mutableStateOf(SplashScreen())

private val KEY_BACKSTACK = "${MainActivity::class.java.name}_BACK_STACK"

class MainActivity : ComponentActivity() {
  private lateinit var scope: CoroutineScope
  private var haveReadExternalPermission = false
  private val composeStateChanger = makeAppComposeStateChanger()
  private lateinit var backstack: Backstack
  private val playerServiceConnection = MediaPlayerServiceConnection(this)
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)
  private var mediaController: ToqueMediaController = NullMediaController
  private var currentQueue: PlayableMediaQueue<*> = NullPlayableMediaQueue
  private var currentQueueJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    backstack = Navigator.configure()
      .addStateChangeCompletionListener { stateChange ->
        topOfStackState.value = stateChange.topNewKey()
      }
      .setGlobalServices(getGlobalServicesBuilder().build())
      .setScopedServices(DefaultServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, makeInitialHistory())

    setContent {
      ToqueTheme {
        ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
          ProvideScreenConfig(
            screenConfig = makeScreenConfig(
              LocalConfiguration.current,
              LocalDensity.current,
              LocalWindowInsets.current
            )
          )
          {
            BackstackProvider(backstack) {
              val topOfStack: ComposeKey by remember { topOfStackState }
              MainScreen(
                composeStateChanger = composeStateChanger,
                topOfStack = topOfStack,
                goToNowPlaying = { backstack.jumpToRoot() },
                goToLibrary = { backstack.goToAboveRoot(LibraryCategoriesScreen()) },
                goToQueue = { backstack.goToAboveRoot(QueueScreen()) },
                goToSearch = { backstack.goToAboveRoot(SearchScreen()) },
                goToSettings = { backstack.goTo(AppSettingsScreen(PrimarySettings)) }
              )
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    playerServiceConnection.unbind()
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
          gainedReadExternalPermission()
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putParcelable(KEY_BACKSTACK, backstack.toBundle())
    super.onSaveInstanceState(outState)
  }

  fun gainedReadExternalPermission() {
    if (!haveReadExternalPermission) {
      haveReadExternalPermission = true
      playerServiceConnection.mediaController
        .onStart { playerServiceConnection.bind() }
        .onEach { controller -> handleControllerChange(controller) }
        .onCompletion { cause -> LOG._i(cause) { it("mediaController flow completed") } }
        .launchIn(scope)
    }
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    mediaController = controller
    if (controller !== NullMediaController) {
      currentQueueJob = controller.currentQueue
        .onStart { LOG._i { it("start currentQueue flow") } }
        .onEach { queue -> handleQueueChange(queue) }
        .onCompletion { cause -> LOG._i(cause) { it("currentQueue flow completed") } }
        .launchIn(scope)
    } else {
      currentQueueJob?.cancel()
      handleQueueChange(NullPlayableMediaQueue)
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    val currentType = currentQueue.queueType
    val newType = queue.queueType
    if (haveReadPermission() && currentType != newType) {
      when (newType) {
        QueueType.Audio -> handleAudioQueue()
        QueueType.NullQueue -> handleNullQueue()
        QueueType.Video -> TODO()
        QueueType.Radio -> TODO()
        QueueType.AudioCast -> TODO()
      }
    }
  }

  private fun handleAudioQueue() {

    if (topOfStackState.value is SplashScreen)
      backstack.setHistory(History.of(NowPlayingScreen()), REPLACE)
  }

  private fun handleNullQueue() {
    backstack.setHistory(History.of(SplashScreen()), REPLACE)
  }

  private fun getGlobalServicesBuilder() = GlobalServices.builder().apply {
    add(this@MainActivity)
    add(playerServiceConnection)
    add(LocalAudioQueueModel(playerServiceConnection, appPrefsSingleton))
    add(LocalAudioMiniPlayerViewModel(playerServiceConnection))
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
