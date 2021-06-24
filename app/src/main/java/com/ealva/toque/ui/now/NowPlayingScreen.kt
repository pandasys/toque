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

package com.ealva.toque.ui.now

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.google.accompanist.insets.ExperimentalAnimatedInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.systemBarsPadding
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.Parcelize

private val LOG by lazyLogger(NowPlayingScreen::class)

@Immutable
@Parcelize
data class NowPlayingScreen(private val noArgPlaceholder: String = "") : ComposeKey() {
  @Suppress("RemoveExplicitTypeArguments")
  override fun bindServices(serviceBinder: ServiceBinder) {
    super.bindServices(serviceBinder)
    with(serviceBinder) {
      add(NowPlayingViewModel(lookup(), lookup()))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    LOG._i { it("NowPlaying ScreenComposable") }
//    val viewModel = rememberService<DogListViewModel>()
//
//    val dogs = viewModel.dogList.subscribeAsState(OptionalWrapper.absent())
    NowPlaying()
    LOG._i { it("after NowPlaying") }
  }
}

@OptIn(ExperimentalAnimatedInsets::class)
@Composable
fun NowPlaying() {
  ProvideWindowInsets(windowInsetsAnimationsEnabled = true) {
    LOG._i { it("NowPlaying Greeting") }
    Greeting(name = "Toque")
  }
}

@Composable
fun Greeting(name: String) {
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
  val systemUiController = rememberSystemUiController()
  val useDarkIcons = MaterialTheme.colors.isLight
  SideEffect {
    systemUiController.setSystemBarsColor(
      color = Color.Transparent,
      darkIcons = useDarkIcons,
    )
  }
  Surface(modifier = Modifier.fillMaxSize()) {
    Text(
      text = "Hello $name! ${if (isPortrait) "Portrait" else "Landscape"}",
      modifier = Modifier
        .systemBarsPadding()
        .navigationBarsPadding(bottom = false)
    )
  }
}
