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

package com.ealva.toque.ui.preset

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e
import com.ealva.toque.service.media.EqPresetFactory
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.component.KoinComponent

@Suppress("unused")
private val LOG by lazyLogger(EqPresetEditorModel::class)

interface EqPresetEditorModel {
  companion object {
    operator fun invoke(
      eqPresetFactory: EqPresetFactory,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): EqPresetEditorModel = EqPresetEditorModelImpl(eqPresetFactory, dispatcher)
  }
}

private class EqPresetEditorModelImpl(
  private val eqPresetFactory: EqPresetFactory,
  dispatcher: CoroutineDispatcher
) : EqPresetEditorModel, ScopedServices.Registered, ScopedServices.Activated, Bundleable {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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

  override fun toBundle(): StateBundle = StateBundle().apply {
    LOG._e { it("toBundle") }
  }

  override fun fromBundle(bundle: StateBundle?) {
    LOG._e { it("fromBundle") }
    bundle?.let { restoreFromBundle(bundle) }
  }

  private fun restoreFromBundle(bundle: StateBundle) {
    LOG._e { it("restoreFromBundle") }
  }
}
