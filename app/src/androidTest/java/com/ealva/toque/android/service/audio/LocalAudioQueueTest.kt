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

package com.ealva.toque.android.service.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.audio.AddAt
import com.ealva.toque.service.audio.ListShuffler
import com.ealva.toque.service.audio.NullPlayableAudioItem
import com.ealva.toque.service.audio.PlayableAudioItem
import com.ealva.toque.service.audio.addNewItems
import com.ealva.toque.service.audio.index
import com.ealva.toque.service.audio.queue
import com.ealva.toque.test.service.audio.PlayableAudioItemFake
import com.nhaarman.expect.expect
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

private val STARTING_QUEUE = listOf(
  PlayableAudioItemFake(id = MediaId(100), instanceId = InstanceId(1), title = Title("A")),
  PlayableAudioItemFake(id = MediaId(200), instanceId = InstanceId(2), title = Title("B")),
  PlayableAudioItemFake(id = MediaId(300), instanceId = InstanceId(3), title = Title("C")),
  PlayableAudioItemFake(id = MediaId(400), instanceId = InstanceId(4), title = Title("D"))
)

private val NEW_QUEUE_ITEMS = listOf(
  PlayableAudioItemFake(id = MediaId(500), instanceId = InstanceId(5), title = Title("E")),
  PlayableAudioItemFake(id = MediaId(600), instanceId = InstanceId(6), title = Title("F"))
)

/**
 * We are only concerned about [shuffleInPlace] being called and return the unshuffled list. No
 * need to test the library shuffle()
 */
class ShufflerFake : ListShuffler() {
  var shuffleCalled = false
  override fun shuffleInPlace(list: MutableList<PlayableAudioItem>) {
    shuffleCalled = true
  }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class LocalAudioQueueTest {
  @Test
  fun testAddNewItemsAfterCurrentAtZero() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        STARTING_QUEUE,
        NEW_QUEUE_ITEMS,
        0,
        AllowDuplicates(false),
        AddAt.AfterCurrent,
        shuffleMode,
        expectedInstanceIdList = listOf(1, 5, 6, 2, 3, 4)
      )
    }
  }

  @Test
  fun testAddNewItemsAfterCurrentAtOne() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        STARTING_QUEUE,
        NEW_QUEUE_ITEMS,
        1,
        AllowDuplicates(false),
        AddAt.AfterCurrent,
        shuffleMode,
        expectedInstanceIdList = listOf(1, 2, 5, 6, 3, 4)
      )
    }
  }

  @Test
  fun testAddNewItemsAfterCurrentAtEnd() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        STARTING_QUEUE,
        NEW_QUEUE_ITEMS,
        STARTING_QUEUE.indices.last,
        AllowDuplicates(false),
        AddAt.AtEnd,
        shuffleMode,
        listOf(1, 2, 3, 4, 5, 6)
      )
    }
  }

  @Test
  fun testAddNewItemsAtEnd() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        STARTING_QUEUE,
        NEW_QUEUE_ITEMS,
        0,
        AllowDuplicates(false),
        AddAt.AtEnd,
        shuffleMode,
        listOf(1, 2, 3, 4, 5, 6)
      )
    }
  }

  @Test
  fun testAddNewItemsAtCurrentEmptyQueue() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        emptyList(),
        NEW_QUEUE_ITEMS,
        -1,
        AllowDuplicates(false),
        AddAt.AfterCurrent,
        shuffleMode,
        expectedInstanceIdList = listOf(5, 6),
        expectedIndex = 0
      )
    }
  }

  @Test
  fun testAddNewItemsAtEndEmptyQueue() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        emptyList(),
        NEW_QUEUE_ITEMS,
        -1,
        AllowDuplicates(false),
        AddAt.AtEnd,
        shuffleMode,
        listOf(5, 6),
        0
      )
    }
  }

  @Test
  fun testAddNewItemsEmptyList() {
    listOf(ShuffleMode.None, ShuffleMode.Media).forEach { shuffleMode ->
      doTestAddNewItems(
        STARTING_QUEUE,
        emptyList(),
        0,
        AllowDuplicates(false),
        AddAt.AtEnd,
        shuffleMode,
        listOf(1, 2, 3, 4),
        0
      )
      doTestAddNewItems(
        STARTING_QUEUE,
        emptyList(),
        0,
        AllowDuplicates(false),
        AddAt.AfterCurrent,
        shuffleMode,
        listOf(1, 2, 3, 4),
        0
      )
      doTestAddNewItems(
        emptyList(),
        emptyList(),
        -1,
        AllowDuplicates(false),
        AddAt.AfterCurrent,
        shuffleMode,
        emptyList(),
        -1
      )
      doTestAddNewItems(
        emptyList(),
        emptyList(),
        -1,
        AllowDuplicates(false),
        AddAt.AtEnd,
        shuffleMode,
        emptyList(),
        -1
      )
    }
  }

  /**
   * Test adding new items to [startingQueue]
   *
   * Nothing will actually ever be shuffled, so [expectedInstanceIdList] should be the result as
   * if [shuffleMode] = [ShuffleMode.None]. The only test related to shuffling is that
   * [ListShuffler.shuffleInPlace] is called (not testing library shuffle function)
   */
  private fun doTestAddNewItems(
    startingQueue: List<PlayableAudioItem>,
    newQueueItems: List<PlayableAudioItem>,
    currentIndex: Int,
    allowDuplicates: AllowDuplicates,
    addAt: AddAt,
    shuffleMode: ShuffleMode,
    expectedInstanceIdList: List<Long>,
    expectedIndex: Int = currentIndex
  ) {
    val shufflerFake = ShufflerFake()
    startingQueue.addNewItems(
      newQueueItems,
      currentIndex,
      if (currentIndex < 0) NullPlayableAudioItem else startingQueue[currentIndex],
      newQueueItems.idSet,
      allowDuplicates,
      addAt,
      shuffleMode,
      shufflerFake
    ).let { queueInfo ->
      expect(queueInfo.index).toBe(expectedIndex)
      queueInfo.queue.let { queue ->
        expect(queue.size).toBe(expectedInstanceIdList.size)
        queue.expectInstanceIds(expectedInstanceIdList)
        if (shuffleMode.shuffleMedia().value) {
          expect(shufflerFake.shuffleCalled)
        }
      }
    }
  }
}

val List<PlayableAudioItem>.idSet: LongSet
  get() = mapTo(LongOpenHashSet(size)) { item ->
    item.id.value
  }

fun List<PlayableAudioItem>.expectInstanceIds(ids: List<Long>) {
  expect(ids.size).toBe(size)
  forEachIndexed { index, item -> expect(item.instanceId).toBe(InstanceId(ids[index])) }
}
