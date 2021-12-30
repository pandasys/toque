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
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.notify.ServiceNotification
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.ui.common.DialogPrompt
import com.ealva.toque.ui.nav.setScreenHistory
import com.ealva.toque.ui.now.NowPlayingScreen
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.StateChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private val LOG by lazyLogger(MainViewModel::class)

interface MainViewModel : MainBridge {
  val currentQueue: StateFlow<PlayableMediaQueue<*>>

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

  fun clearPrompt()

  fun gainedReadExternalPermission()

  companion object {
    operator fun invoke(
      mainBridge: MainBridge,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): MainViewModel = MainViewModelImpl(mainBridge, backstack, dispatcher)
  }
}

private class MainViewModelImpl(
  private val mainBridge: MainBridge,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : MainViewModel, ScopedServices.Registered, MainBridge by mainBridge {
  private lateinit var scope: CoroutineScope
  private lateinit var playerServiceConnection: MediaPlayerServiceConnection
  private var mediaController: ToqueMediaController = NullMediaController
  override var currentQueue = MutableStateFlow<PlayableMediaQueue<*>>(NullPlayableMediaQueue)
  private var currentQueueJob: Job? = null

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

  override fun clearPrompt() {
    prompt(DialogPrompt.None)
  }

  override fun gainedReadExternalPermission() {
    playerServiceConnection.mediaController
      .onStart { playerServiceConnection.bind() }
      .onEach { controller -> handleControllerChange(controller) }
      .onCompletion { cause -> LOG._i(cause) { it("mediaController flow completed") } }
      .launchIn(scope)
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    if (mediaController !== controller) {
      mediaController = controller
      if (controller !== NullMediaController) {
        currentQueueJob = controller.currentQueue
          .onStart { LOG._i { it("start currentQueue flow") } }
          .onEach { queue -> handleQueueChange(queue) }
          .catch { cause -> LOG.e(cause) { it("currentQueue flow error") } }
          .onCompletion { LOG._i { it("currentQueue flow completed") } }
          .launchIn(scope)
      } else {
        currentQueueJob?.cancel()
        currentQueueJob = null
        handleQueueChange(NullPlayableMediaQueue)
      }
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    val currentType = currentQueue.value.queueType
    val newType = queue.queueType
    if (currentType != newType) {
      when (newType) {
        QueueType.Audio -> handleAudioQueue()
        QueueType.NullQueue -> handleNullQueue()
        QueueType.Video -> TODO()
        QueueType.Radio -> TODO()
      }
      currentQueue.value = queue
    }
  }

  private fun handleAudioQueue() {
    if (backstack.root<ComposeKey>() !is NowPlayingScreen)
      backstack.setScreenHistory(History.of(NowPlayingScreen()), StateChange.REPLACE)
  }

  private fun handleNullQueue() {
    backstack.setScreenHistory(History.of(SplashScreen()), StateChange.REPLACE)
  }

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    playerServiceConnection = MediaPlayerServiceConnection(Toque.appContext)
  }

  override fun onServiceUnregistered() {
    playerServiceConnection.unbind()
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
