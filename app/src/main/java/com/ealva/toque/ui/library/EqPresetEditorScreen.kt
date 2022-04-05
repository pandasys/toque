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

package com.ealva.toque.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ealva.ealvalog.e
import com.ealva.toque.navigation.ComposeKey
import com.zhuinden.simplestack.ScopedServices
import kotlinx.parcelize.Parcelize
import javax.annotation.concurrent.Immutable
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorScreen::class)

@Immutable
@Parcelize
data class EqPresetEditorScreen(private val noArg: String = "") : ComposeKey() {
  override fun bindServices(serviceBinder: ServiceBinder) {
    serviceBinder.add(EqPresetEditorModel())
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
//    val viewModel = rememberService<EqPresetEditorModel>()
    Column(modifier = Modifier.fillMaxSize()) {
      Text(text = "Search")
    }
  }
}

class EqPresetEditorModel : ScopedServices.Registered, ScopedServices.Activated {
  init {
    LOG.e { it("SearchModel") }
  }

  override fun onServiceRegistered() {
    LOG.e { it("onServiceRegistered") }
  }

  override fun onServiceUnregistered() {
    LOG.e { it("onServiceUnregistered") }
  }

  override fun onServiceActive() {
    LOG.e { it("onServiceActive") }
  }

  override fun onServiceInactive() {
    LOG.e { it("onServiceInactive") }
  }

}
