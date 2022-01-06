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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ealva.toque.persist.asMediaIdList
import com.ealva.toque.service.audio.LocalAudioQueue
import com.ealva.toque.service.audio.LocalAudioQueueState
import com.ealva.toque.service.audio.NullLocalAudioQueue
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.ui.audio.LocalAudioQueueViewModel
import com.ealva.toque.ui.common.LibraryScrollBar
import com.ealva.toque.ui.common.scrollToFirst
import com.ealva.toque.ui.common.scrollToPosition
import com.ealva.toque.ui.common.visibleIndices
import com.ealva.toque.ui.common.LocalScreenConfig
import com.ealva.toque.ui.library.AudioMediaInfoScreen
import com.ealva.toque.ui.library.QueueItemsActionBar
import com.ealva.toque.ui.library.SelectedItems
import com.ealva.toque.ui.library.SelectedItemsFlow
import com.ealva.toque.ui.library.SongListItem
import com.ealva.toque.ui.library.asState
import com.ealva.toque.ui.library.clearSelection
import com.ealva.toque.ui.library.deselect
import com.ealva.toque.ui.library.filterIfHasSelection
import com.ealva.toque.ui.library.ifInSelectionModeToggleElse
import com.ealva.toque.ui.library.inSelectionModeThenTurnOff
import com.ealva.toque.ui.library.selectAll
import com.ealva.toque.ui.library.toggleSelection
import com.ealva.toque.ui.library.turnOffSelectionMode
import com.ealva.toque.ui.nav.goToScreen
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlinx.coroutines.CoroutineDispatcher
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
import org.burnoutcrew.reorderable.ReorderableState
import org.burnoutcrew.reorderable.detectReorder
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
      add(QueueViewModel(lookup(), backstack))
    }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<QueueViewModel>()
    val queueState = viewModel.queueState.collectAsState()
    val selectedItems = viewModel.selectedFlow.asState()
    val reorderState = rememberReorderState()

    val listState = reorderState.listState
    val scope = rememberCoroutineScope()

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(bottom = false)
    ) {
      val state = queueState.value
      val queue = state.queue
      val queueIndex = state.queueIndex
      val selected = selectedItems.value

      QueueItemsActionBar(
        itemCount = queue.size,
        inSelectionMode = selected.inSelectionMode,
        selectedCount = selected.selectedCount,
        goToCurrent = { listState.scrollToPosition(scope, queueIndex) },
        goToTop = { listState.scrollToFirst(scope) },
        goToBottom = { listState.scrollToPosition(scope, queue.indices.last) },
        addToPlaylist = { viewModel.addToPlaylist() },
        selectAllOrNone = { all -> if (all) viewModel.selectAll() else viewModel.clearSelection() },
        mediaInfoClicked = { viewModel.displayMediaInfo() }
      )

      QueueContents(
        queue = queue,
        index = state.queueIndex,
        selectedItems = selected,
        reorderState = reorderState,
        swapItems = { index1, index2 -> viewModel.swapViewItems(index1, index2) },
        moveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
        onDelete = { queueItem -> viewModel.deleteQueueItem(queueItem) },
        onClick = { queueItem -> viewModel.itemClicked(queueItem) },
        onLongClick = { queueItem -> viewModel.itemLongClicked(queueItem) },
        scrollToPosition = { position -> listState.scrollToPosition(scope, position) }
      )
    }
  }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueContents(
  queue: List<QueueAudioItem>,
  index: Int,
  selectedItems: SelectedItems<InstanceId>,
  reorderState: ReorderableState,
  swapItems: (Int, Int) -> Unit,
  moveQueueItem: (Int, Int) -> Unit,
  onDelete: (QueueAudioItem) -> Unit,
  onClick: (QueueAudioItem) -> Unit,
  onLongClick: (QueueAudioItem) -> Unit,
  scrollToPosition: (Int) -> Unit
) {
  var scrollToCurrent by remember { mutableStateOf(true) }
  val listState = reorderState.listState
  val config = LocalScreenConfig.current

  fun scrollToCurrent(position: Int) {
    scrollToCurrent = false
    scrollToPosition(position)
  }

  if (scrollToCurrent && index in queue.indices && index !in listState.visibleIndices) {
    scrollToCurrent(index)
  }

  LibraryScrollBar(
    listState = listState,
    modifier = Modifier
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
          state = reorderState,
          onMove = { from, to -> swapItems(from.index, to.index) },
          onDragEnd = { start, end -> moveQueueItem(start, end) }
        )
        .navigationBarsPadding(bottom = false)
    ) {
      items(items = queue, key = { it.instanceId }) { queueItem ->
        val dismissState = rememberDismissState()
        if (dismissState.isDismissed(DismissDirection.EndToStart)) {
          onDelete(queueItem)
          LaunchedEffect(key1 = queueItem.instanceId) {
            // Need to reset dismiss state so swipe doesn't occur again when undoing onDelete
            dismissState.snapTo(DismissValue.Default)
          }
        }

        val currentId =
          if (index in queue.indices) queue[index].instanceId else InstanceId.INVALID
        val isCurrent = queueItem.item.instanceId == currentId
        val isSelected = selectedItems.isSelected(queueItem.instanceId)

        DismissibleItem(
          dismissState = dismissState,
          modifier = Modifier.draggedItem(reorderState.offsetByKey(queueItem.instanceId))
        ) {
          QueueItem(
            audioItem = queueItem,
            isCurrent = isCurrent,
            isSelected = isSelected,
            modifier = Modifier.combinedClickable(
              onLongClick = { onLongClick(queueItem) },
              onClick = { onClick(queueItem) }
            )
          ) {
            DragHandle(
              index = queueItem.position,
              modifier = Modifier.detectReorder(reorderState)
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
 fun DismissibleItem(
  dismissState: DismissState,
  modifier: Modifier,
  item: @Composable () -> Unit,
) {
  SwipeToDismiss(
    state = dismissState,
    directions = setOf(DismissDirection.EndToStart),
    dismissThresholds = { FractionalThreshold(0.55f) },
    background = {
      if (dismissState.offset.value.roundToInt() < -50) {
        Row(
          modifier = Modifier
            .background(Color.Red)
            .fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Spacer(modifier = Modifier.weight(1F))
          Icon(
            painter = rememberImagePainter(data = R.drawable.ic_trashcan),
            contentDescription = "Delete indicator",
            modifier = Modifier.size(38.dp),
            tint = LocalContentColor.current
          )
          Spacer(modifier = Modifier.width(18.dp))
        }
      }
    },
    modifier = modifier
  ) {
    item()
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItem(
  audioItem: QueueAudioItem,
  isCurrent: Boolean,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  icon: @Composable () -> Unit,
) {
  with(audioItem) {
    SongListItem(
      songTitle = title,
      albumTitle = albumTitle,
      artistName = artist,
      songDuration = duration,
      rating = rating,
      highlightBackground = isSelected,
      icon = icon,
      modifier = modifier,
      textColor = if (isCurrent) Color.Green else Color.Unspecified
    )
  }
}

@Composable
private fun DragHandle(index: Int, modifier: Modifier) {
  Column(
    modifier = modifier,
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

interface QueueViewModel {
  val queueState: StateFlow<QueueState>
  val selectedFlow: SelectedItemsFlow<InstanceId>
  val inDragMode: StateFlow<Boolean>

  fun selectAll()
  fun clearSelection()

  fun swapViewItems(index1: Int, index2: Int)
  fun moveQueueItem(from: Int, to: Int)
  fun deleteQueueItem(item: QueueAudioItem)
  fun goToQueueItemMaybePlay(item: QueueAudioItem)

  fun itemClicked(item: QueueAudioItem)
  fun itemLongClicked(item: QueueAudioItem)

  /**
   * Gathers either the selected queue items, or all items if there is no selection, and
   * adds them to a playlist. The user is asked to select a playlist or create one.
   */
  fun addToPlaylist()

  fun displayMediaInfo()

  companion object {
    operator fun invoke(
      localAudioQueueModel: LocalAudioQueueViewModel,
      backstack: Backstack,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): QueueViewModel =
      QueueViewModelImpl(localAudioQueueModel, backstack, dispatcher)
  }
}

private class QueueViewModelImpl(
  private val localAudioQueueModel: LocalAudioQueueViewModel,
  private val backstack: Backstack,
  private val dispatcher: CoroutineDispatcher
) : QueueViewModel, ScopedServices.Activated, ScopedServices.HandlesBack {
  private lateinit var scope: CoroutineScope
  private var currentQueueJob: Job? = null
  private var queueStateJob: Job? = null
  private var localAudioQueue: LocalAudioQueue = NullLocalAudioQueue

  override val queueState = MutableStateFlow(QueueState(emptyList(), -1))
  override val selectedFlow = SelectedItemsFlow<InstanceId>(SelectedItems())
  override val inDragMode = MutableStateFlow(false)

  override fun selectAll() = selectedFlow.selectAll(getSongKeys())
  private fun getSongKeys() = queueState.value.queue.mapTo(mutableSetOf()) { it.instanceId }
  override fun clearSelection() = selectedFlow.clearSelection()
  override fun onBackEvent(): Boolean = selectedFlow.inSelectionModeThenTurnOff()

  override fun swapViewItems(index1: Int, index2: Int) {
    inDragMode.value = true
    queueState.update {
      val queue = it.queue
      val prevCurrentIndex = it.queueIndex
      val newIndex = if (index1 == prevCurrentIndex) {
        index2
      } else if (index1 < prevCurrentIndex) {
        if (index2 >= prevCurrentIndex) {
          prevCurrentIndex - 1
        } else {
          prevCurrentIndex
        }
      } else if (index2 <= prevCurrentIndex) {
        if (index1 > prevCurrentIndex) {
          prevCurrentIndex + 1
        } else {
          prevCurrentIndex
        }
      } else prevCurrentIndex

      it.copy(
        queue = queue
          .toMutableList()
          .swapQueueItems(index1, index2),
        queueIndex = newIndex
      )
    }
  }

  override fun moveQueueItem(from: Int, to: Int) {
    inDragMode.value = false
    if (from != to) localAudioQueue.moveQueueItem(from, to)
  }

  override fun deleteQueueItem(item: QueueAudioItem) {
    selectedFlow.deselect(item.instanceId)
    scope.launch { localAudioQueue.removeFromQueue(item.position, item) }
  }

  override fun goToQueueItemMaybePlay(item: QueueAudioItem) {
    localAudioQueue.goToIndexMaybePlay(item.position)
  }

  override fun itemClicked(item: QueueAudioItem) =
    selectedFlow.ifInSelectionModeToggleElse(item.instanceId) { goToQueueItemMaybePlay(item) }

  override fun itemLongClicked(item: QueueAudioItem) =
    selectedFlow.toggleSelection(item.instanceId)

  override fun addToPlaylist() {
    val queue = queueState.value.queue
    val mediaIdList = getSelectedItems(queue)
      .mapTo(LongArrayList(queue.size)) { queueAudioItem -> queueAudioItem.id.value }
      .asMediaIdList
    scope.launch {
      if (localAudioQueueModel.addToPlaylist(mediaIdList).wasExecuted)
        selectedFlow.turnOffSelectionMode()
    }
  }

  override fun displayMediaInfo() {
    val selected = selectedFlow.value

    if (selected.selectedCount == 1) {
      val instanceId: InstanceId = selected.single()

      backstack.goToScreen(
        AudioMediaInfoScreen(
          queueState.value
            .queue
            .first { item -> item.instanceId == instanceId }
            .item
            .id
        )
      )
    }
  }

  private fun getSelectedItems(queue: List<QueueAudioItem>) =
    queue.filterIfHasSelection(selectedFlow.value) { it.instanceId }

  override fun onServiceActive() {
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    currentQueueJob = localAudioQueueModel.localAudioQueue
      .onEach { queue -> handleQueueChange(queue) }
      .launchIn(scope)
  }

  override fun onServiceInactive() {
    currentQueueJob?.cancel()
    currentQueueJob = null
    handleQueueChange(NullLocalAudioQueue)
    scope.cancel()
  }

  private fun handleQueueChange(queue: PlayableMediaQueue<*>) {
    when (queue) {
      is NullLocalAudioQueue -> queueInactive()
      is LocalAudioQueue -> queueActive(queue)
      else -> queueInactive()
    }
  }

  private fun queueActive(queue: LocalAudioQueue) {
    localAudioQueue = queue
    queueStateJob = queue.queueState
      .onEach { state -> handleServiceState(state) }
      .catch { cause -> LOG.e(cause) { it("Error with LocalAudioQueueState flow") } }
      .onCompletion { LOG._i { it("LocalAudioQueue state flow completed") } }
      .launchIn(scope)
  }

  private fun queueInactive() {
    queueStateJob?.cancel()
    queueStateJob = null
    localAudioQueue = NullLocalAudioQueue
  }

  private fun handleServiceState(localAudioQueueState: LocalAudioQueueState) {
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

fun MutableList<QueueAudioItem>.swapQueueItems(index1: Int, index2: Int) = apply {
  val atIndex1 = this[index1]
  val atIndex2 = this[index2]
  this[index1] = QueueAudioItem(atIndex2.item, atIndex1.position)
  this[index2] = QueueAudioItem(atIndex1.item, atIndex2.position)
}
