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

import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueueType

interface PlayableQueueFactory {
  fun make(queueType: QueueType, servicePrefs: PlayerServicePrefs): PlayableMediaQueue<*>

  companion object {
    operator fun invoke(): PlayableQueueFactory = PlayableQueueFactoryImpl()
  }
}

class PlayableQueueFactoryImpl : PlayableQueueFactory {
  override fun make(queueType: QueueType, servicePrefs: PlayerServicePrefs): PlayableMediaQueue<*> {
    return when (queueType) {
      QueueType.Audio -> makeLocalAudioQueue(servicePrefs)
      else -> throw IllegalStateException("Unknown QueueType")
    }
  }

  private fun makeLocalAudioQueue(servicePrefs: PlayerServicePrefs): PlayableMediaQueue<*> {
    return LocalAudioQueue(servicePrefs)
  }
}
