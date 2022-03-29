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
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestackextensions.servicesktx.add

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
