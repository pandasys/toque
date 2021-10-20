/*
 * Copyright 2021 eAlva.com
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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.log._e
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private val LOG by lazyLogger(LocalAudioMiniPlayerViewModel::class)

data class MiniPlayerState(
  val queue: List<AudioItem>,
  val queueIndex: Int,
  val playingState: PlayState,
) {
  companion object {
    val NONE = MiniPlayerState(
      queue = emptyList(),
      queueIndex = -1,
      playingState = PlayState.Stopped
    )
  }
}

interface LocalAudioMiniPlayerViewModel {
  val miniPlayerState: StateFlow<MiniPlayerState>

  fun togglePlayPause()
  fun goToQueueIndexMaybePlay(index: Int)

  companion object {
    operator fun invoke(
      serviceConnection: MediaPlayerServiceConnection
    ): LocalAudioMiniPlayerViewModel =
      LocalAudioMiniPlayerViewModelImpl(serviceConnection)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class LocalAudioMiniPlayerViewModelImpl(
  private val serviceConnection: MediaPlayerServiceConnection
) : LocalAudioMiniPlayerViewModel {
  private var scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var mediaController: ToqueMediaController = NullMediaController
  private var controllerJob: Job? = null
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val miniPlayerState = MutableStateFlow(MiniPlayerState.NONE).apply {
    scope.launch {
      subscriptionCount.collect { count ->
        when (count) {
          0 -> noSubscribers()
          else -> haveSubscriber()
        }
      }
    }
  }

  private fun haveSubscriber() {
    controllerJob = serviceConnection.mediaController
      .onEach { controller -> handleControllerChange(controller) }
      .onCompletion { handleControllerChange(NullMediaController) }
      .launchIn(scope)
  }

  private fun noSubscribers() {
    controllerJob?.cancel()
    controllerJob = null
    handleControllerChange(NullMediaController)
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    mediaController = controller
    if (controller !== NullMediaController) {
      currentQueueJob = controller.currentQueue
        .onEach { queue -> handleQueueChange(queue) }
        .launchIn(scope)
    } else {
      currentQueueJob?.cancel()
      currentQueueJob = null
      handleQueueChange(NullPlayableMediaQueue)
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    when (queue.queueType) {
      QueueType.Audio -> queueActive(queue as LocalAudioQueue)
      else -> queueInactive()
    }
  }

  private fun queueActive(queue: LocalAudioQueue) {
    audioQueue = queue
    queueStateJob = audioQueue.queueState
      .onStart { LOG._e { it("MiniPlayer start collecting queueState") } }
      .onEach { state -> handleServiceState(state) }
      .catch { cause -> LOG.e(cause) { it("") } }
      .onCompletion { LOG._e { it("MiniPlayer completed collecting queueState") } }
      .launchIn(scope)
  }

  private fun queueInactive() {
    queueStateJob?.cancel()
    queueStateJob = null
    audioQueue = NullLocalAudioQueue
  }

  private suspend fun handleServiceState(queueState: LocalAudioQueueState) {
    val newState = miniPlayerState.value.copy(
      queue = queueState.queue,
      queueIndex = queueState.queueIndex,
      playingState = queueState.playingState,
    )
    miniPlayerState.emit(newState)
  }

  override fun togglePlayPause() {
    scope.launch { audioQueue.togglePlayPause() }
  }

  override fun goToQueueIndexMaybePlay(index: Int) {
    scope.launch { audioQueue.goToIndexMaybePlay(index) }
  }
}
