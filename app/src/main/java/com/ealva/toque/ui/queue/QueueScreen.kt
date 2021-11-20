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

package com.ealva.toque.ui.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.navigation.ComposeKey
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.ui.audio.LocalAudioQueueModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.SongListItem
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.draggedItem
import org.burnoutcrew.reorderable.rememberReorderState
import org.burnoutcrew.reorderable.reorderable
import javax.annotation.concurrent.Immutable
import kotlin.math.roundToInt

private val LOG by lazyLogger(QueueScreen::class)

@Immutable
@Parcelize
data class QueueScreen(private val noArg: String = "") : ComposeKey() {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) {
      add(QueueViewModel(lookup()))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<QueueViewModel>()
    val queueState = viewModel.queueState.collectAsState()

    QueueContents(
      queue = queueState.value.queue,
      index = queueState.value.queueIndex,
      swapItems = { from, to ->
        viewModel.swapQueueItemsInView(from.toQueueItemPosition(), to.toQueueItemPosition())
      },
      moveQueueItem = { index1, index2 -> viewModel.moveQueueItem(index1, index2)},
      onDelete = { viewModel.deleteQueueItem(it) },
      onClick = { viewModel.goToQueueItemMaybePlay(it) }
    )
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun QueueContents(
  queue: List<QueueAudioItem>,
  index: Int,
  swapItems: (ItemPosition, ItemPosition) -> Unit,
  moveQueueItem: (Int, Int) -> Unit,
  onDelete: (QueueAudioItem) -> Unit,
  onClick: (QueueAudioItem) -> Unit
) {
  var scrollToCurrent by remember { mutableStateOf(true) }
  val state = rememberReorderState()
  val listState = state.listState
  val config = LocalScreenConfig.current
  val coroutineScope = rememberCoroutineScope()

  fun scrollToCurrent(scope: CoroutineScope, state: LazyListState, position: Int) {
    scope.launch { state.scrollToItem(position) }
  }

  LOG._e { it("Recomposing QueueContents") }

  if (scrollToCurrent && index in queue.indices && index !in listState.visibleIndices) {
    scrollToCurrent = false
    listState.firstVisibleItemIndex
    listState.layoutInfo.visibleItemsInfo
    scrollToCurrent(coroutineScope, listState, index)
  }

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
      .padding(top = 18.dp, bottom = config.getNavPlusBottomSheetHeight(isExpanded = true))
  ) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(
        start = 8.dp,
        top = 8.dp,
        bottom = config.getListBottomContentPadding(isExpanded = true),
        end = 8.dp
      ),
      modifier = Modifier
        .reorderable(
          state = state,
          onMove = { from, to -> swapItems(from, to) },
          onDragEnd = { start, end -> moveQueueItem(start, end) }
        )
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      items(items = queue, key = { it.instanceId }) { queueItem ->
        val dismissState = rememberDismissState()
        val dismissDirection = dismissState.dismissDirection
        val isDismissed = dismissState.isDismissed(DismissDirection.EndToStart)

        if (isDismissed && dismissDirection == DismissDirection.EndToStart) {
          val scope = rememberCoroutineScope()
          scope.launch {
            onDelete(queueItem)
          }
        }
        SwipeToDismiss(
          state = dismissState,
          directions = setOf(DismissDirection.EndToStart),
          dismissThresholds = { FractionalThreshold(0.55f) },
          background = {
            if (dismissState.offset.value.roundToInt() < -50) {
              Box(
                Modifier
                  .background(Color.Red.copy(alpha = .5F))
                  .fillMaxSize()
              )
            }
          },
          modifier = Modifier
            .draggedItem(state.offsetByKey(queueItem.instanceId))
            .detectReorderAfterLongPress(state)
        ) {
          val currentId =
            if (index in queue.indices) queue[index].instanceId else InstanceId.INVALID
          val isCurrent = queueItem.item.instanceId == currentId
          QueueItem(
            audioItem = queueItem,
            isCurrent = isCurrent,
            modifier = Modifier
              .clickable(
                enabled = !isCurrent,
                onClick = { onClick(queueItem) }
              )
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItem(
  audioItem: QueueAudioItem,
  isCurrent: Boolean,
  modifier: Modifier = Modifier
) {
  with(audioItem) {
    SongListItem(
      songTitle = title,
      albumTitle = albumTitle,
      artistName = artist,
      songDuration = duration,
      rating = rating,
      highlightBackground = isCurrent,
      icon = { DragHandle(position) },
      modifier = modifier
    )
  }
}

@Composable
private fun DragHandle(index: Int) {
  Column(
    horizontalAlignment = Alignment.End
  ) {
    Text(
      text = "${index + 1}.",
      textAlign = TextAlign.Center,
      maxLines = 1,
      style = MaterialTheme.typography.overline,
    )
    Icon(
      painter = rememberImagePainter(data = R.drawable.ic_drag_handle),
      contentDescription = "Drag handle",
      modifier = Modifier.size(44.dp),
      tint = LocalContentColor.current
    )
  }
}

@Immutable
data class QueueAudioItem(val item: AudioItem, val position: Int) : AudioItem by item

@Immutable
data class QueueState(
  val queue: List<QueueAudioItem>,
  val queueIndex: Int,
)

fun ItemPosition.toQueueItemPosition(): QueueItemPosition =
  QueueItemPosition(position = index, id = key as? InstanceId ?: InstanceId.INVALID)

data class QueueItemPosition(
  val position: Int,
  val id: InstanceId
)

interface QueueViewModel {
  val queueState: StateFlow<QueueState>

  val inDragMode: StateFlow<Boolean>

  fun swapQueueItemsInView(fromItemPos: QueueItemPosition, toItemPos: QueueItemPosition)
  fun moveQueueItem(from: Int, to: Int)
  fun deleteQueueItem(item: QueueAudioItem)
  fun goToQueueItemMaybePlay(item: QueueAudioItem)

  companion object {
    operator fun invoke(localAudioQueueModel: LocalAudioQueueModel): QueueViewModel =
      QueueViewModelImpl(localAudioQueueModel)
  }
}

private class QueueViewModelImpl(
  private val localAudioQueueModel: LocalAudioQueueModel,
) : QueueViewModel, ScopedServices.Registered, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var audioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val queueState = MutableStateFlow(QueueState(emptyList(), -1))
  override val inDragMode = MutableStateFlow(false)

  override fun swapQueueItemsInView(fromItemPos: QueueItemPosition, toItemPos: QueueItemPosition) {
    inDragMode.value = true
    queueState.update {
      val from = fromItemPos.position
      val to = toItemPos.position
      val queue = it.queue
      val prevCurrentIndex = it.queueIndex
      val newIndex = if (from == prevCurrentIndex) {
        to
      } else if (from < prevCurrentIndex) {
        if (to >= prevCurrentIndex) {
          prevCurrentIndex - 1
        } else {
          prevCurrentIndex
        }
      } else if (to <= prevCurrentIndex) {
        if (from > prevCurrentIndex) {
          prevCurrentIndex + 1
        } else {
          prevCurrentIndex
        }
      } else prevCurrentIndex

      it.copy(
        queue = queue
          .toMutableList()
          .swapQueueItems(from, to),
        queueIndex = newIndex
      )
    }
  }

  override fun moveQueueItem(from: Int, to: Int) {
    inDragMode.value = false
    if (from != to)  audioQueue.moveQueueItem(from, to)
  }

  override fun deleteQueueItem(item: QueueAudioItem) {
    audioQueue.removeFromQueue(item.position, item)
  }

  override fun goToQueueItemMaybePlay(item: QueueAudioItem) {
    audioQueue.goToIndexMaybePlay(item.position)
  }

  override fun onServiceRegistered() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  }

  override fun onServiceUnregistered() {
    scope.cancel()
  }

  override fun onServiceActive() {
    currentQueueJob = localAudioQueueModel.localAudioQueue
      .onEach { queue -> handleQueueChange(queue) }
      .launchIn(scope)
  }

  override fun onServiceInactive() {
    currentQueueJob?.cancel()
    currentQueueJob = null
    handleQueueChange(NullLocalAudioQueue)
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    when (queue) {
      is NullLocalAudioQueue -> queueInactive()
      is LocalAudioQueue -> queueActive(queue)
      else -> queueInactive()
    }
  }

  private fun queueActive(queue: LocalAudioQueue) {
    audioQueue = queue
    queueStateJob = audioQueue.queueState
      .onEach { state -> handleServiceState(state) }
      .catch { cause -> LOG.e(cause) { it("") } }
      .onCompletion { LOG._i { it("LocalAudioQueue state flow completed") } }
      .launchIn(scope)
  }

  private fun queueInactive() {
    queueStateJob?.cancel()
    queueStateJob = null
    audioQueue = NullLocalAudioQueue
  }

  private fun handleServiceState(localAudioQueueState: LocalAudioQueueState) {
    val currentIndex = queueState.value.queueIndex
    if (!inDragMode.value) {
      queueState.update {
        it.copy(
          queue = localAudioQueueState.queue.toQueueList(),
          queueIndex = localAudioQueueState.queueIndex
        )
      }
    }
  }
}

private fun List<AudioItem>.toQueueList(): List<QueueAudioItem> =
  mapIndexedTo(ArrayList(size)) { index, item -> QueueAudioItem(item, index) }

val LazyListState.visibleIndices: IntRange
  get() = firstVisibleItemIndex..(layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)

fun MutableList<QueueAudioItem>.swapQueueItems(index1: Int, index2: Int) = apply {
  val atIndex1 = this[index1]
  val atIndex2 = this[index2]
  this[index1] = QueueAudioItem(atIndex2.item, atIndex1.position)
  this[index2] = QueueAudioItem(atIndex1.item, atIndex2.position)
}
