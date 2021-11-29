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

package com.ealva.toque.ui.main

import androidx.compose.material.SnackbarDuration
import com.ealva.toque.service.notify.ServiceNotification
import com.ealva.toque.ui.common.DialogPrompt
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface MainViewModel {
  /**
   * A flow of [Notification] which would typically be displayed to the user as a Snackbar
   */
  val notificationFlow: Flow<Notification>

  fun notify(notification: Notification)

  fun notify(serviceNotification: ServiceNotification)

  /**
   * If the [DialogPrompt] has a [DialogPrompt.prompt], it should be displayed to the user.
   */
  val promptFlow: StateFlow<DialogPrompt>

  fun prompt(prompt: DialogPrompt)

  companion object {
    operator fun invoke(
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): MainViewModel = MainViewModelImpl(dispatcher)
  }
}

private class MainViewModelImpl(
  private val dispatcher: CoroutineDispatcher
) : MainViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val notificationFlow = MutableSharedFlow<Notification>()
  override fun notify(notification: Notification) {
    scope.launch { notificationFlow.emit(notification) }
  }

  override fun notify(serviceNotification: ServiceNotification) {
    scope.launch {
      notificationFlow.emit(
        Notification(
          msg = serviceNotification.msg,
          action = ServiceActionWrapper(serviceNotification.action),
          duration = serviceNotification.duration.asSnackbarDuration(),
        )
      )
    }
  }

  override val promptFlow = MutableStateFlow<DialogPrompt>(DialogPrompt.None)
  override fun prompt(prompt: DialogPrompt) {
    promptFlow.value = prompt
  }

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
  }

  override fun onServiceInactive() {
    scope.cancel()
  }
}

private class ServiceActionWrapper(
  private val serviceAction: ServiceNotification.Action
) : Notification.Action {
  override val label: String? get() = serviceAction.label
  override fun action() = serviceAction.action()
  override fun expired() = serviceAction.expired()
}

private fun ServiceNotification.Duration.asSnackbarDuration(): SnackbarDuration {
  return when (this) {
    ServiceNotification.Duration.Notify -> SnackbarDuration.Short
    ServiceNotification.Duration.Important -> SnackbarDuration.Long
    ServiceNotification.Duration.WaitForReply -> SnackbarDuration.Indefinite
  }
}
