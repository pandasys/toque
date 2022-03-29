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

package com.ealva.toque.ui.main

import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.ThemeChoice
import com.zhuinden.simplestack.ScopedServices
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
