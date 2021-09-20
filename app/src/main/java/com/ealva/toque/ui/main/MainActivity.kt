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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.R.string.AccessExternalStorage
import com.ealva.toque.R.string.RepeatRequiredToScanRationale
import com.ealva.toque.R.string.RequestPermission
import com.ealva.toque.R.string.RequiredToScanRationale
import com.ealva.toque.R.string.SettingsScanRationale
import com.ealva.toque.android.content.haveReadPermission
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
import com.ealva.toque.ui.now.NowPlayingScreen
import com.ealva.toque.ui.theme.ToqueTheme
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission as RequestPerm

private val LOG by lazyLogger(MainActivity::class)

class MainActivity : AppCompatActivity() {
  private lateinit var scope: CoroutineScope
  private val composeStateChanger = ComposeStateChanger()
  private lateinit var backstack: Backstack
  private var launcher: ActivityResultLauncher<String> = makeRequestReadExternalLauncher()
  private val playerServiceConnection = MediaPlayerServiceConnection(this)
  private var mediaController: ToqueMediaController = NullMediaController
  private var currentQueue: PlayableMediaQueue<*> = NullPlayableMediaQueue
  private var currentQueueJob: Job? = null
  private val appPrefsSingleton: AppPrefsSingleton by inject(named("AppPrefs"))
//  private val audioMediaDao: AudioMediaDao by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    backstack = Navigator.configure()
      .setGlobalServices(getGlobalServicesBuilder().build())
      .setScopedServices(DefaultServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, makeInitialHistory())

    setContent {
      ToqueTheme {
        BackstackProvider(backstack) {
          Box(Modifier.fillMaxSize()) {
            composeStateChanger.RenderScreen()
          }
        }
      }
    }
    if (haveReadPermission()) gainedReadExternalPermission() else requestReadExternalPermission()
  }

  override fun onDestroy() {
    super.onDestroy()
    playerServiceConnection.unbind()
  }

  private fun makeRequestReadExternalLauncher() =
    registerForActivityResult(RequestPerm()) { isGranted: Boolean ->
      if (isGranted) {
        gainedReadExternalPermission()
      } else {
        if (showReadRationale()) {
          AlertDialog.Builder(this)
            .setTitle(AccessExternalStorage)
            .setMessage(RequiredToScanRationale)
            .setPositiveButton(R.string.OK) { _, _ ->
              requestReadExternalPermission()
            }
            .create()
            .show()
        } else {
          val goSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
          AlertDialog.Builder(this)
            .setTitle(AccessExternalStorage)
            .setMessage(if (goSettings) SettingsScanRationale else RepeatRequiredToScanRationale)
            .setPositiveButton(if (goSettings) R.string.Settings else RequestPermission) { _, _ ->
              if (goSettings) {
                startActivity(
                  Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                  }
                )
              } else {
                requestReadExternalPermission()
              }
            }
            .setNegativeButton(R.string.Exit) { _, _ ->
              finishAfterTransition()
            }
            .create()
            .show()
        }
      }
    }

  private fun requestReadExternalPermission() {
    launcher.launch(READ_EXTERNAL_STORAGE)
  }

  private fun gainedReadExternalPermission() {
    scope.launch {
      playerServiceConnection.mediaController
        .onStart { playerServiceConnection.bind() }
        .onEach { controller -> handleControllerChange(controller) }
        .onCompletion { cause -> LOG._i(cause) { it("mediaController flow completed") } }
        .collect()
    }
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    mediaController = controller
    if (controller !== NullMediaController) {
      currentQueueJob = scope.launch {
        controller.currentQueue
          .onStart { LOG._i { it("start currentQueue flow") } }
          .onEach { queue -> handleQueueChange(queue) }
          .onCompletion { cause -> LOG._i(cause) { it("currentQueue flow completed") } }
          .collect()
      }
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
    add(appPrefsSingleton)
    add(playerServiceConnection)
  }

  private val globalServices get() = (application as Toque).globalServicesBuilder

  override fun onBackPressed() {
    if (!Navigator.onBackPressed(this)) {
      super.onBackPressed()
    }
  }

  private fun makeInitialHistory(): History<ComposeKey> = History.of(SplashScreen())

  private fun showReadRationale(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    shouldShowRequestPermissionRationale(READ_EXTERNAL_STORAGE)
  } else {
    false
  }
}


