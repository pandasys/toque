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

package com.ealva.toque.navigation

import android.os.Parcelable
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.core.DefaultComposeKey
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import kotlinx.parcelize.Parcelize

abstract class ComposeKey : DefaultComposeKey(), Parcelable, DefaultServiceProvider.HasServices,
  AllowableNavigation {
  override val saveableStateProviderKey: Any
    get() = this

  override fun getScopeTag(): String = javaClass.name

  override fun bindServices(serviceBinder: ServiceBinder) {
  }

  /**
   * If allowed to navigate away from the top screen, perform the [command]. By default all
   * navigation commands are allowed. Editor screens may disallow navigation until the user has been
   * prompted.
   */
  override fun navigateIfAllowed(command: () -> Unit) {
    command()
  }
}

@Parcelize
object NullComposeKey : ComposeKey() {
  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    Text(text = "NULL")
  }
}
