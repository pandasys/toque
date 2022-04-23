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

package com.ealva.toque.ui.now

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.zhuinden.simplestack.ScopeKey
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val NOW_PLAYING_SCREEN_MODEL_SERVICE_TAG = "NowPlayingScreenViewModel"

@Immutable
@Parcelize
data class NowPlayingScreen(
  private val noArg: String = ""
) : ComposeKey(), ScopeKey.Child, KoinComponent {
  override fun getParentScopes(): List<String> = listOf(
    LocalAudioQueueViewModel::class.java.name
  )

  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(
        NowPlayingViewModel(backstack, get(), lookup(), get(AppPrefs.QUALIFIER)),
        NOW_PLAYING_SCREEN_MODEL_SERVICE_TAG
      )
    }
  }

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<NowPlayingViewModel>(NOW_PLAYING_SCREEN_MODEL_SERVICE_TAG)

    val nowPlayingState = viewModel.nowPlayingState.collectAsState()

    NowPlaying(
      state = nowPlayingState.value,
      goToIndex = { index -> viewModel.goToQueueIndexMaybePlay(index) },
      togglePlayPause = { viewModel.togglePlayPause() },
      next = { viewModel.nextMedia() },
      prev = { viewModel.previousMedia() },
      nextList = { viewModel.nextList() },
      prevList = { viewModel.previousList() },
      seekTo = { position -> viewModel.seekTo(position) },
      userSeekingComplete = { viewModel.userSeekingComplete() },
      toggleShowRemaining = { viewModel.toggleShowTimeRemaining() },
      toggleEqMode = { viewModel.toggleEqMode() },
      nextRepeatMode = { viewModel.nextRepeatMode() },
      nextShuffleMode = { viewModel.nextShuffleMode() },
      showItemDialog = { viewModel.showCurrentItemDialog() },
      modifier = modifier
    )
  }
}
