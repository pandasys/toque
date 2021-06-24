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

package com.ealva.toque.service.audio

import android.media.AudioManager
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.app.Toque
import com.ealva.toque.common.Title
import com.ealva.toque.db.QueueId
import com.ealva.toque.db.QueueState
import com.ealva.toque.db.QueueStateDaoFactory
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.service.PlayerServicePrefs
import com.ealva.toque.service.audio.LocalAudioQueue.Companion.QUEUE_ID
import com.ealva.toque.service.queue.MusicStreamVolume
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueuePrefs
import com.ealva.toque.service.queue.QueuePrefsSingleton
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.StreamVolume
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val LOG by lazyLogger(LocalAudioQueue::class)

interface LocalAudioQueue : PlayableMediaQueue<AudioQueueItem> {
  override val queueType: QueueType
    get() = QueueType.Audio

  fun addToUpNext(mediaIdList: MediaIdList)

  companion object {
    const val QUEUE_ID = 1L
    operator fun invoke(servicePrefs: PlayerServicePrefs): LocalAudioQueue =
      LocalAudioQueueImpl(servicePrefs)
  }
}

private class LocalAudioQueueImpl(
  servicePrefs: PlayerServicePrefs
) : LocalAudioQueue, KoinComponent {
  private val queueStateDaoFactory: QueueStateDaoFactory by inject()
  private val queueStateDao = queueStateDaoFactory.makeQueueStateDao(QueueId(QUEUE_ID))
  private val audioManager: AudioManager by inject()
  private val scope = CoroutineScope(Dispatchers.Main + Job())

  private var prefs: QueuePrefs = QueuePrefs.NULL
  private var queueState: QueueState = QueueState.INACTIVE_QUEUE_STATE

  private var upNextQueue: List<AudioQueueItem> = emptyList()
  private var upNextShuffled: List<AudioQueueItem> = emptyList()

  override val isActive = MutableStateFlow(false).asStateFlow()
  private inline val active: Boolean get() = isActive.value

  override val streamVolume: StreamVolume = MusicStreamVolume(audioManager)

  override suspend fun activate(
    resume: Boolean,
    startPlayer: Boolean,
    haveWritePermission: Boolean
  ) {
    prefs = queuePrefsSingleton.instance()
    queueState = when (val result = queueStateDao.getState()) {
      is Ok -> result.value
      is Err -> QueueState.INACTIVE_QUEUE_STATE.also {
        LOG.e { it("Can't read queue state %s", result.error) }
      }
    }
  }

  override fun deactivate() {
    scope.cancel()
  }

  private val inShuffleMode: Boolean
    get() = prefs.shuffleMode().shuffleMedia

  override val queue: List<AudioQueueItem>
    get() = if (inShuffleMode) upNextShuffled else upNextQueue
  override val currentItem: AudioQueueItem
    get() = queue[currentItemIndex]
  override val currentItemIndex: Int
    get() = queueState.queueIndex

  fun getItemFromIndex(index: Int): AudioQueueItem =
    if (index in queue.indices) queue[index] else NullAudioQueueItem

  override suspend fun getNextMediaTitle(): Title {
    TODO("Not yet implemented")
  }

  override fun play(immediate: Boolean) {
//    if (isActive.value)
  }

  override fun pause(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun togglePlayPause() {
    TODO("Not yet implemented")
  }

  override fun next() {
    TODO("Not yet implemented")
  }

  override fun previous() {
    TODO("Not yet implemented")
  }

  override fun goToIndexMaybePlay(index: Int) {
    TODO("Not yet implemented")
  }

  override fun addToUpNext(mediaIdList: MediaIdList) {
    TODO("Not yet implemented")
  }
}

private const val QUEUE_PREFS_FILE_NAME = "LocalAudioQueue"
private val queuePrefsSingleton: QueuePrefsSingleton = QueuePrefsSingleton(
  QueuePrefs.Companion::make,
  Toque.appContext,
  QUEUE_PREFS_FILE_NAME
)
