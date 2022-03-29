/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.ui.lock

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.BuildConfig
import com.ealva.toque.android.content.onBroadcast
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.ProvideScreenConfig
import com.ealva.toque.ui.common.makeScreenConfig
import com.ealva.toque.ui.main.MainBridge
import com.ealva.toque.ui.main.ServiceProvider
import com.ealva.toque.ui.main.ThemeViewModel
import com.ealva.toque.ui.main.ToqueGlobalServicesFactory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@Suppress("unused")
private val LOG by lazyLogger(LockScreenActivity::class)

class LockScreenActivity : ComponentActivity(), MainBridge {
  private lateinit var scope: CoroutineScope
  private val appPrefsSingleton: AppPrefsSingleton by inject(AppPrefs.QUALIFIER)
  private val keyguardManager: KeyguardManager by inject()
  private lateinit var backstack: Backstack
  private val composeStateChanger = ComposeStateChanger()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    scope = lifecycleScope

    WindowCompat.setDecorFitsSystemWindows(window, false) // we'll handle the system insets

    onBroadcast(IntentFilter(Intent.ACTION_USER_PRESENT)) { intent ->
      if (intent?.action == Intent.ACTION_USER_PRESENT) finish()
    }

    onBroadcast(IntentFilter(Intent.ACTION_SCREEN_OFF)) { intent ->
      if (intent?.action == Intent.ACTION_SCREEN_OFF) maybeExitOnScreenOff()
    }

    backstack = Navigator.configure()
      .setGlobalServices(ToqueGlobalServicesFactory(this, appPrefsSingleton))
      .setScopedServices(ServiceProvider())
      .setStateChanger(AsyncStateChanger(composeStateChanger))
      .install(this, androidContentFrame, History.of(LockPlayerScreen()))

    setContent {
      BackstackProvider(backstack) {
        val themeViewModel = rememberService<ThemeViewModel>()
        val themeChoice by themeViewModel.themeChoice.collectAsState()

        ToqueTheme(themeChoice) {
          ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
            ProvideScreenConfig(
              screenConfig = makeScreenConfig(
                LocalConfiguration.current,
                LocalDensity.current,
                LocalWindowInsets.current
              )
            ) {
              MainLockScreen(composeStateChanger = composeStateChanger)
            }
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (!keyguardManager.isKeyguardLocked) finish()
    scope.launch {
      keepScreenOn(appPrefsSingleton.instance().keepScreenOn())
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(FLAG_SHOW_WHEN_LOCKED)
    }
  }

  private fun keepScreenOn(keepOn: Boolean) {
    if (keepOn) {
      window.addFlags(FLAG_KEEP_SCREEN_ON)
    } else {
      window.clearFlags(FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun maybeExitOnScreenOff() {
    scope.launch {
      try {
        val hideLockScreenPlayer = !shouldStartLockScreenActivity(
          prefs = appPrefsSingleton.instance(),
          isPlaying = backstack.lookup<LocalAudioQueueViewModel>().isPlaying
        )
        if (hideLockScreenPlayer) finish()
      } catch (e: Exception) {
        LOG.e(e) { it("Error checking if playing and maybe exist") }
      }
    }
  }

  companion object {
    private val ACTION_RESULT =
      "${BuildConfig.APPLICATION_ID}.${LockScreenActivity::class.java.simpleName}"
    private val SWITCH_TO_AUDIO = "$ACTION_RESULT.SwitchToAudio"

    fun maybeStart(context: Context, appPrefs: AppPrefs, isPlaying: Boolean) {
      if (shouldStartLockScreenActivity(appPrefs, isPlaying)) start(context)
    }

    private fun shouldStartLockScreenActivity(prefs: AppPrefs, isPlaying: Boolean): Boolean =
      prefs.showLockScreenPlayer() && isPlaying

    private fun start(context: Context) = context.startActivity(
      Intent(context, LockScreenActivity::class.java).apply {
        action = SWITCH_TO_AUDIO
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }

  override val haveReadExternalPermission: Boolean
    get() = checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

  override val activityContext: Context get() = this
  override fun startAppSettingsActivity() = Unit
  override fun exit() = Unit
  override val canDrawOverlays: Boolean = false
  override fun requestOverlayPermission() = Unit
}
