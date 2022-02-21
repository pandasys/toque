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

import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ServiceProvider : DefaultServiceProvider(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    super.bindServices(serviceBinder)

    val scope = serviceBinder.scopeTag

    with(serviceBinder) {
      when (scope) {
        LocalAudioQueueViewModel::class.java.name -> {
          add(LocalAudioQueueViewModel(lookup(), get(AppPrefs.QUALIFIER), get()))
        }
        else -> Unit
      }
    }
  }
}
