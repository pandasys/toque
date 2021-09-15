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

package com.ealva.toque.service

import android.media.AudioManager
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.toque.db.QueueStateDaoFactory
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.PlayableAudioItemFactory
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.session.MediaSessionControl
import com.ealva.toque.service.session.MediaSessionState

interface PlayableQueueFactory {
  suspend fun make(
    queueType: QueueType,
    sessionControl: MediaSessionControl,
    sessionState: MediaSessionState
  ): PlayableMediaQueue<*>

  companion object {
    operator fun invoke(
      queueStateDaoFactory: QueueStateDaoFactory,
      playableAudioItemFactory: PlayableAudioItemFactory,
      audioManager: AudioManager,
      appPrefsSingleton: AppPrefsSingleton
    ): PlayableQueueFactory = PlayableQueueFactoryImpl(
      queueStateDaoFactory,
      playableAudioItemFactory,
      audioManager,
      appPrefsSingleton
    )
  }
}

private val LOG by lazyLogger(PlayableQueueFactory::class)

private class PlayableQueueFactoryImpl(
  private val queueStateDaoFactory: QueueStateDaoFactory,
  private val playableAudioItemFactory: PlayableAudioItemFactory,
  private val audioManager: AudioManager,
  private val appPrefsSingleton: AppPrefsSingleton
) : PlayableQueueFactory {
  override suspend fun make(
    queueType: QueueType,
    sessionControl: MediaSessionControl,
    sessionState: MediaSessionState
  ): PlayableMediaQueue<*> {
    return when (queueType) {
      QueueType.Audio -> makeLocalAudioQueue(sessionControl, sessionState)
      else -> {
        LOG.e { +it("Unknown QueueType=%s illegal state", queueType) }
        throw IllegalStateException("Unknown QueueType")
      }
    }
  }

  private suspend fun makeLocalAudioQueue(
    sessionControl: MediaSessionControl,
    sessionState: MediaSessionState
  ): PlayableMediaQueue<*> {
    return LocalAudioQueue.make(
      sessionControl,
      sessionState,
      queueStateDaoFactory,
      playableAudioItemFactory,
      audioManager,
      appPrefsSingleton.instance()
    )
  }
}
