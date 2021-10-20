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

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.android.content.haveReadPermission
import com.ealva.toque.android.content.inPortrait
import com.ealva.toque.app.Toque
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.ui.now.NowPlayingScreen
import com.ealva.toque.ui.settings.AppSettingsScreen
import com.ealva.toque.ui.settings.SettingScreenKeys
import com.ealva.toque.ui.settings.SettingScreenKeys.PrimarySettings
import com.ealva.toque.ui.theme.ToqueTheme
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

private val LOG by lazyLogger(MainActivity::class)

private val backstackKeyFlow: MutableState<ComposeKey> = mutableStateOf(SplashScreen())

class MainActivity : ComponentActivity() {
  private lateinit var scope: CoroutineScope
  private val composeStateChanger = ComposeStateChanger()
  private lateinit var backstack: Backstack
  private val playerServiceConnection = MediaPlayerServiceConnection(this)
  private var mediaController: ToqueMediaController = NullMediaController
  private var currentQueue: PlayableMediaQueue<*> = NullPlayableMediaQueue
  private var currentQueueJob: Job? = null
  private val appPrefsSingleton: AppPrefsSingleton by inject(named("AppPrefs"))
  private val libVlcPrefsSingleton: LibVlcPrefsSingleton by inject(named("LibVlcPrefs"))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    backstack = Navigator.configure()
      .addStateChangeCompletionListener { stateChange ->
        val composeKey = stateChange.topNewKey<ComposeKey>()
        LOG._e { it("ComposeKey=%s", composeKey) }
        backstackKeyFlow.value = composeKey
      }
      .setGlobalServices(getGlobalServicesBuilder().build())
      .setScopedServices(DefaultServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, makeInitialHistory())

    setContent {
      ToqueTheme {
        ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
          BackstackProvider(backstack) {
            MainScreen(
              composeStateChanger = composeStateChanger,
              backstackKeyFlow = backstackKeyFlow,
              goToSettings = { backstack.goTo(AppSettingsScreen(PrimarySettings)) },
              goToNowPlaying = { backstack.goUp(NowPlayingScreen(), true) }
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    playerServiceConnection.unbind()
  }

  fun gainedReadExternalPermission() {
    playerServiceConnection.mediaController
      .onStart { playerServiceConnection.bind() }
      .onEach { controller -> handleControllerChange(controller) }
      .onCompletion { cause -> LOG._i(cause) { it("mediaController flow completed") } }
      .launchIn(scope)
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
    backstack.setHistory(History.of(NowPlayingScreen()), REPLACE)
  }

  private fun handleNullQueue() {
    backstack.setHistory(History.of(SplashScreen()), REPLACE)
  }

  private fun getGlobalServicesBuilder(): GlobalServices.Builder = globalServices.apply {
    add(this@MainActivity)
    addService("AppPrefs", appPrefsSingleton)
    addService("LibVlcPrefs", libVlcPrefsSingleton)
    add(playerServiceConnection)
    add(LocalAudioMiniPlayerViewModel(playerServiceConnection))
  }

  private val globalServices get() = (application as Toque).globalServicesBuilder

  override fun onBackPressed() {
    if (!Navigator.onBackPressed(this)) super.onBackPressed()
  }

  private fun makeInitialHistory(): History<ComposeKey> = History.of(SplashScreen())
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
  throw IllegalStateException("LocalScaffoldState not provided")
}

@Composable
fun ProvideSnackbarHostState(
  snackbarHostState: SnackbarHostState,
  content: @Composable () -> Unit
) = CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) { content() }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
  composeStateChanger: ComposeStateChanger,
  backstackKeyFlow: MutableState<ComposeKey>,
  goToSettings: () -> Unit,
  goToNowPlaying: () -> Unit
) {
  Surface(modifier = Modifier.fillMaxSize()) {

    val topOfStack: ComposeKey by remember { backstackKeyFlow }
    val config = LocalConfiguration.current
    val scaffoldState = rememberScaffoldState()
    val (isBottomSheetExpanded, expandBottomSheet) = remember { mutableStateOf(false) }

    expandBottomSheet(topOfStack !is SplashScreen && topOfStack !is NowPlayingScreen)

    ProvideSnackbarHostState(snackbarHostState = scaffoldState.snackbarHostState) {
      if (topOfStack is SplashScreen) {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
          scaffoldState = scaffoldState,
        ) {
          composeStateChanger.RenderScreen(modifier = Modifier.fillMaxSize())
        }
      } else {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
          scaffoldState = scaffoldState,
          bottomBar = {
            MainBottomSheet(
              topOfStack = topOfStack,
              isExpanded = isBottomSheetExpanded,
              goToSettings = goToSettings,
              goToNowPlaying = goToNowPlaying,
              modifier = if (config.inPortrait) {
                Modifier
                  .padding(horizontal = 20.dp)
                  .zIndex(1F)
              } else {
                Modifier
                  .padding(horizontal = 8.dp)
                  .zIndex(1F)
              }
            )
          },
          backgroundColor = Color.Transparent
        ) {
          composeStateChanger.RenderScreen(
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }
}
