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

import android.app.KeyguardManager
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
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
import com.ealva.toque.ui.lock.LockPlayerScreen
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val LOG by lazyLogger(MainViewModel::class)

interface MainViewModel : MainBridge {
  /** [currentQueue] contains the active [PlayableMediaQueue] in the MediaPlayerService */
  val currentQueue: StateFlow<PlayableMediaQueue<*>>

  /**
   * A flow of [Notification] which would typically be displayed to the user as a Snackbar. The
   * Notification should be asked to display itself as a snackbar.
   */
  val notificationFlow: Flow<Notification>

  /**
   * If activity is visible or [notification] is of indefinite length, emit or enqueue the
   * notification to be shown to the user.
   */
  fun notify(notification: Notification)

  /** Convert the [serviceNotification] to a [Notification] and call [notify] */
  fun notify(serviceNotification: ServiceNotification)

  /** If the [DialogPrompt] has a [DialogPrompt.prompt], it should be displayed to the user. */
  val promptFlow: StateFlow<DialogPrompt>

  /** Display [prompt] to the user as a dialog */
  fun prompt(prompt: DialogPrompt)

  /** Clear any current dialog prompt */
  fun clearPrompt()

  /**
   * Notify the model that read external permission had been granted
   */
  fun gainedReadExternalPermission()

  /**
   * Tell the model to bind to the MediaPlayerService if the proper permissions have been granted.
   */
  fun bindIfHaveReadExternalPermission()

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
) : MainViewModel, ScopedServices.Registered, KoinComponent, MainBridge by mainBridge {
  private lateinit var scope: CoroutineScope
  private lateinit var playerServiceConnection: MediaPlayerServiceConnection
  private val keyguardManager: KeyguardManager by inject()
  private var mediaController: ToqueMediaController = NullMediaController
  override var currentQueue = MutableStateFlow<PlayableMediaQueue<*>>(NullPlayableMediaQueue)
  private var currentQueueJob: Job? = null

  override val notificationFlow = MutableSharedFlow<Notification>()
  private val notificationDeque = NotificationDeque()
  private var activeNotification: Notification? = null

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    playerServiceConnection = MediaPlayerServiceConnection(mainBridge.activityContext)
  }

  override fun onServiceUnregistered() {
    playerServiceConnection.unbind()
    scope.cancel()
  }

  override fun notify(notification: Notification) {
    if (mainBridge.activityIsVisible || notification.isIndefiniteLength) emitOrEnqueue(notification)
  }

  override fun notify(serviceNotification: ServiceNotification) {
    notify(
      Notification(
        msg = serviceNotification.msg,
        action = ServiceActionWrapper(serviceNotification.action),
        duration = serviceNotification.duration.asSnackbarDuration(),
      )
    )
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
      .catch { cause -> LOG.e(cause) { it("MediaController flow error") } }
      .onCompletion { LOG._i { it("MediaController flow completed") } }
      .launchIn(scope)
  }

  override fun bindIfHaveReadExternalPermission() {
    if (mainBridge.haveReadExternalPermission && playerServiceConnection.isNotBound)
      gainedReadExternalPermission()
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    if (mediaController !== controller) {
      mediaController = controller
      if (controller !== NullMediaController) {
        currentQueueJob = controller.currentQueueFlow
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
    val root = backstack.root<ComposeKey>()
    if (root !is LockPlayerScreen && root !is NowPlayingScreen && keyguardManager.isNotLocked) {
      backstack.setScreenHistory(History.of(NowPlayingScreen()), StateChange.REPLACE)
    }
  }

  private fun handleNullQueue() {
    backstack.setScreenHistory(History.of(SplashScreen()), StateChange.REPLACE)
  }

  private fun notificationActive(notification: Notification) {
    activeNotification = notification
  }

  private fun notificationDismissed() {
    activeNotification = null
    pullFromDequeAndEmit()
  }

  private fun emitOrEnqueue(notification: Notification) {
    activeNotification?.let { active ->
      if (active.isIndefiniteLength) {
        notificationDeque.addLast(notification)
      } else {
        active.dismiss()
        wrapAndEmit(notification)
      }
    } ?: wrapAndEmit(notification)
  }

  private fun pullFromDequeAndEmit() {
    if (notificationDeque.size > 0) wrapAndEmit(notificationDeque.getFirst())
  }

  private fun wrapAndEmit(notification: Notification) {
    scope.launch {
      notificationFlow.emit(WrappedNotification(notification))
    }
  }

  /**
   * Wrap a notification so we can wrap the action and control showing the snackbar. This is done
   * so the model is aware if a snackbar is currently being displayed (in conjunction with the
   * wrapped action, [NotificationAction].
   */
  private inner class WrappedNotification(
    private val notification: Notification
  ) : Notification by notification {
    override val action: Notification.Action = NotificationAction(notification.action)

    override suspend fun showSnackbar(snackbarHostState: SnackbarHostState) {
      notificationActive(notification)
      notification.showSnackbar(snackbarHostState)
    }
  }

  /**
   * Wrap a [Notification.Action] so we can monitor when the snackbar is dismissed.
   */
  private inner class NotificationAction(
    private val action: Notification.Action
  ) : Notification.Action {
    private var notifiedDismissed = false

    override val label: String?
      get() = action.label

    override fun action() {
      action.action()
      snackbarDismissed()
    }

    override fun expired() {
      action.expired()
      snackbarDismissed()
    }

    private fun snackbarDismissed() {
      if (!notifiedDismissed) {
        notifiedDismissed = true
        notificationDismissed()
      }
    }
  }
}

private class ServiceActionWrapper(
  private val serviceAction: ServiceNotification.Action
) : Notification.Action {
  override val label: String? get() = serviceAction.label
  override fun action() = serviceAction.action()
  override fun expired() = serviceAction.expired()
}

private fun ServiceNotification.NotificationDuration.asSnackbarDuration(): SnackbarDuration {
  return when (this) {
    ServiceNotification.NotificationDuration.Notify -> SnackbarDuration.Short
    ServiceNotification.NotificationDuration.Important -> SnackbarDuration.Long
    ServiceNotification.NotificationDuration.WaitForReply -> SnackbarDuration.Indefinite
  }
}

private inline val KeyguardManager.isNotLocked: Boolean get() = !isKeyguardLocked

class NotificationDeque {
  private var deque = ArrayDeque<Notification>(4)
  val size: Int get() = deque.size

  fun addLast(notification: Notification) {
    deque = deque
      .map { item -> if (notification.isIndefiniteLength) item else null }
      .filterNotNullTo(ArrayDeque(deque.size + 1))
      .apply { addLast(notification) }
  }

  fun getFirst(): Notification = deque.removeFirst()
}
