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

package com.ealva.toque.ui.audio

import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.service.MediaPlayerServiceConnection
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.audio.TransitionType
import com.ealva.toque.service.controller.NullMediaController
import com.ealva.toque.service.controller.ToqueMediaController
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.NullPlayableMediaQueue
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val LOG by lazyLogger(LocalAudioQueueModel::class)

interface LocalAudioQueueModel {
  sealed interface PlayResult {
    val needPrompt: Boolean

    object Executed : PlayResult {
      override val needPrompt = false
    }

    interface PromptRequired : PlayResult {
      val itemCount: Int
      fun execute(clearUpNext: ClearQueue)
    }
  }

  val localAudioQueue: StateFlow<LocalAudioQueue>

  val playUpNextAction: StateFlow<PlayUpNextAction>

  fun play(audioIdList: AudioIdList, onPlay: () -> Unit): PlayResult
  fun shuffle(audioIdList: AudioIdList, onShuffle: () -> Unit): PlayResult
  fun playNext(audioIdList: AudioIdList)
  fun addToUpNext(audioIdList: AudioIdList)

  companion object {
    operator fun invoke(
      serviceConnection: MediaPlayerServiceConnection,
      appPrefsSingleton: AppPrefsSingleton,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): LocalAudioQueueModel =
      LocalAudioQueueModelImpl(serviceConnection, appPrefsSingleton, dispatcher)
  }
}

private data class PrefsHolder(val appPrefs: AppPrefs? = null)

class LocalAudioQueueModelImpl(
  private val serviceConnection: MediaPlayerServiceConnection,
  private val appPrefsSingleton: AppPrefsSingleton,
  private val dispatcher: CoroutineDispatcher
) : LocalAudioQueueModel, ScopedServices.Registered, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var controllerJob: Job? = null

  private val prefsHolder = MutableStateFlow(PrefsHolder())

  override val localAudioQueue = MutableStateFlow<LocalAudioQueue>(NullLocalAudioQueue)

  override val playUpNextAction = MutableStateFlow(PlayUpNextAction.Prompt)

  private inner class PromptRequiredImpl(
    private val audioIdList: AudioIdList,
    private val shuffle: Boolean,
    private val onPlay: () -> Unit
  ) : LocalAudioQueueModel.PlayResult.PromptRequired {
    override val itemCount: Int
      get() = audioIdList.size

    override fun execute(clearUpNext: ClearQueue) {
      doPlay(audioIdList, clearUpNext, shuffle, onPlay)
    }

    override val needPrompt: Boolean = true
  }

  override fun play(
    audioIdList: AudioIdList,
    onPlay: () -> Unit
  ): LocalAudioQueueModel.PlayResult = playOrPrompt(audioIdList, false, onPlay)

  private fun playOrPrompt(audioIdList: AudioIdList, shuffle: Boolean, onPlay: () -> Unit) =
    if (playUpNextAction.value.shouldPrompt) {
      PromptRequiredImpl(audioIdList = audioIdList, shuffle = shuffle, onPlay)
    } else {
      doPlay(
        audioIdList,
        playUpNextAction.value.clearUpNext,
        shuffleMedia = shuffle,
        onPlay = onPlay
      )
      LocalAudioQueueModel.PlayResult.Executed
    }

  private fun doPlay(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    shuffleMedia: Boolean,
    onPlay: () -> Unit
  ) {
    if (shuffleMedia) audioIdList.shuffled()
    localAudioQueue.value.playNext(
      if (shuffleMedia) audioIdList.shuffled() else audioIdList,
      clearUpNext,
      PlayNow(true),
      TransitionType.Manual
    )
    onPlay()
  }

  override fun shuffle(
    audioIdList: AudioIdList,
    onShuffle: () -> Unit
  ): LocalAudioQueueModel.PlayResult = playOrPrompt(audioIdList, true, onShuffle)

  override fun playNext(audioIdList: AudioIdList) {
    localAudioQueue.value.playNext(
      audioIdList,
      ClearQueue(false),
      PlayNow(false),
      TransitionType.Manual
    )
  }

  override fun addToUpNext(audioIdList: AudioIdList) {
    localAudioQueue.value.addToUpNext(audioIdList)
  }

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    scope.launch {
      val appPrefs = appPrefsSingleton.instance()
      prefsHolder.value = PrefsHolder(appPrefs)
      appPrefs.playUpNextAction
        .asFlow()
        .onEach { playUpNextAction.value = it }
        .launchIn(scope)
    }
  }

  override fun onServiceActive() {
    controllerJob = serviceConnection.mediaController
      .onEach { controller -> handleControllerChange(controller) }
      .onCompletion { handleControllerChange(NullMediaController) }
      .launchIn(scope)
  }

  private fun handleControllerChange(controller: ToqueMediaController) {
    if (controller !== NullMediaController) {
      controller.currentQueue
        .onEach { queue -> handleQueueChange(queue) }
        .launchIn(scope)
    } else {
      handleQueueChange(NullPlayableMediaQueue)
    }
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) = when (queue) {
    is NullLocalAudioQueue -> queueInactive()
    is LocalAudioQueue -> queueActive(queue)
    else -> queueInactive()
  }

  private fun queueInactive() {
    localAudioQueue.value = NullLocalAudioQueue
  }

  private fun queueActive(queue: LocalAudioQueue) {
    localAudioQueue.value = queue
  }

  override fun onServiceInactive() {
    controllerJob?.cancel()
    controllerJob = null
    handleControllerChange(NullMediaController)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }
}
