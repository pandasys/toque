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
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.app.Toque
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.LoggingCoroutineExceptionHandler
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.common.debug
import com.ealva.toque.common.debugCheck
import com.ealva.toque.common.ensureUiThread
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.QueueDao
import com.ealva.toque.db.QueueState
import com.ealva.toque.db.QueueStateDaoFactory
import com.ealva.toque.db.SongListType
import com.ealva.toque.db.getNextList
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.log.logExecTime
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toRating
import com.ealva.toque.service.media.toStarRating
import com.ealva.toque.service.player.AvPlayer.Companion.OFFSET_CONSIDERED_END
import com.ealva.toque.service.player.CrossFadeTransition
import com.ealva.toque.service.player.DirectTransition
import com.ealva.toque.service.player.NoOpMediaTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PlayerTransitionPair
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.MusicStreamVolume
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueuePrefs
import com.ealva.toque.service.queue.QueuePrefsSingleton
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.StreamVolume
import com.ealva.toque.service.scrobble.NullScrobbler
import com.ealva.toque.service.scrobble.Scrobbler
import com.ealva.toque.service.scrobble.ScrobblerFacade
import com.ealva.toque.service.scrobble.ScrobblerFactory
import com.ealva.toque.service.session.AudioFocusManager
import com.ealva.toque.service.session.MediaSessionControl
import com.ealva.toque.service.session.MediaSessionState
import com.ealva.toque.service.session.Metadata
import com.ealva.toque.service.session.PlaybackActions
import com.ealva.toque.service.session.PlaybackState
import com.ealva.toque.service.session.toCompat
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import it.unimi.dsi.fastutil.longs.LongCollection
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

typealias QueueList = List<PlayableAudioItem>
typealias QueueItemList = List<MediaSessionCompat.QueueItem>
typealias QueueItemArrayList = ArrayList<MediaSessionCompat.QueueItem>

private val LOG by lazyLogger(LocalAudioQueue::class)
private val LOGGING_HANDLER = LoggingCoroutineExceptionHandler(LOG)
private val MAIN_WITH_HANDLER = Dispatchers.Main + LOGGING_HANDLER
private const val MAX_QUEUE_RETRIES = 5

typealias QueueInfo = Pair<QueueList, Int>

inline val QueueInfo.queue: QueueList
  get() = first

inline val QueueInfo.index: Int
  get() = second

private const val QUEUE_PREFS_FILE_NAME = "LocalAudioQueue"
private val queuePrefsSingleton: QueuePrefsSingleton = QueuePrefsSingleton(
  QueuePrefs.Companion::make,
  Toque.appContext,
  QUEUE_PREFS_FILE_NAME
)

open class ListShuffler {
  open fun shuffleInPlace(list: MutableList<PlayableAudioItem>) = list.shuffle()
}

val LIST_SHUFFLER = ListShuffler()

interface LocalAudioQueue : PlayableMediaQueue<AudioItem> {
  /*
    internal val queue: List<AudioItem>,
  val queueIndex: Int,
  val position: Millis,
  val duration: Millis,
  val playingState: PlayState,
  val repeatMode: RepeatMode,
  val shuffleMode: ShuffleMode,
  val eqMode: EqMode,
  val presets: List<EqPreset>,
  val currentPreset: EqPreset,
  val extraMediaInfo: String
   */

  override val queueType: QueueType
    get() = QueueType.Audio

  val seeking: Boolean
  val isSeekable: Boolean

  val manualTransition: PlayerTransitionPair
  val autoAdvanceTransition: PlayerTransitionPair

  val repeatMode: RepeatMode
  val shuffleMode: ShuffleMode

  suspend fun setRepeatMode(mode: RepeatMode)
  suspend fun setShuffleMode(mode: ShuffleMode)

  suspend fun setRating(rating: StarRating)

  suspend fun addToUpNext(audioIdList: AudioIdList)

  suspend fun playNext(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transition: PlayerTransitionPair = manualTransition
  )

  suspend fun goToQueueItem(instanceId: Long)
  suspend fun prepareNext(audioIdList: AudioIdList)

  companion object {
    fun make(
      sessionControl: MediaSessionControl,
      sessionState: MediaSessionState,
      queueStateDaoFactory: QueueStateDaoFactory,
      playableAudioItemFactory: PlayableAudioItemFactory,
      audioManager: AudioManager,
      appPrefs: AppPrefs
    ): LocalAudioQueue = LocalAudioCommandProcessor(
      LocalAudioQueueImpl(
        sessionState,
        queueStateDaoFactory,
        playableAudioItemFactory,
        MusicStreamVolume(audioManager),
        appPrefs
      ),
      appPrefs,
      sessionControl
    )
  }
}

private const val SEEK_TO_ZERO_POSITION = 5000

@Suppress("SameParameterValue", "LargeClass")
private class LocalAudioQueueImpl(
  private val sessionState: MediaSessionState,
  queueStateDaoFactory: QueueStateDaoFactory,
  private val itemFactory: PlayableAudioItemFactory,
  override val streamVolume: StreamVolume,
  private val appPrefs: AppPrefs
) : LocalAudioQueue, KoinComponent {
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private val queueStateDao = queueStateDaoFactory.makeQueueStateDao(AudioMediaDao.QUEUE_ID)

  private var prefs: QueuePrefs = QueuePrefs.NULL
  private var queueState: QueueState = QueueState.INACTIVE_QUEUE_STATE
  private val audioMediaDao: AudioMediaDao by inject()
  private val queueDao: QueueDao by inject()
  private val scrobblerFactory: ScrobblerFactory by inject()
  private var scrobbler: Scrobbler = NullScrobbler
  private var currentItemFlowJob: Job? = null
  private var playState: PlayState = PlayState.None

  override var seeking = false
  override val isSeekable: Boolean
    get() = currentItem.isSeekable

  override val repeatMode: RepeatMode
    get() = prefs.repeatMode()
  inline val repeatCurrent: Boolean
    get() = repeatMode.current
  inline val repeatQueue: Boolean
    get() = repeatMode.queue

  suspend fun nextRepeatMode() = setRepeatMode(repeatMode.getNext())
  override suspend fun setRepeatMode(mode: RepeatMode) = prefs.repeatMode.set(mode)

  override val shuffleMode: ShuffleMode
    get() = prefs.shuffleMode()
  private inline val shuffleMedia: Boolean
    get() = shuffleMode.shuffleMedia
  private inline val shuffleLists: Boolean
    get() = shuffleMode.shuffleLists

  override suspend fun setShuffleMode(mode: ShuffleMode) = prefs.shuffleMode.set(mode)

  override suspend fun setRating(rating: StarRating) {
    currentItem.setRating(rating.toRating())
    updateMetadata()
  }

  override val queue: List<PlayableAudioItem>
    get() = if (shuffleMedia) upNextShuffled else upNextQueue
  override val currentItem: PlayableAudioItem
    get() = getItemFromIndex(currentItemIndex)
  override val currentItemIndex: Int
    get() = queueState.queueIndex
  private val nextItem: PlayableAudioItem
    get() = getItemFromIndex(currentItemIndex + 1)

  private var upNextQueue: QueueList = mutableListOf()
  private var upNextShuffled: QueueList = mutableListOf()

  override val manualTransition: PlayerTransitionPair
    get() = if (appPrefs.manualChangeFade()) {
      CrossFadeTransition(
        appPrefs.manualChangeFadeLength(),
        appPrefs.audioOutputModule().forceStartVolumeZero
      )
    } else {
      DirectTransition()
    }

  override val autoAdvanceTransition: PlayerTransitionPair
    get() = if (appPrefs.autoAdvanceFade()) {
      CrossFadeTransition(
        appPrefs.autoAdvanceFadeLength(),
        appPrefs.audioOutputModule().forceStartVolumeZero
      )
    } else {
      DirectTransition()
    }

  override val isActive = MutableStateFlow(false)
  override var enabledActions: PlaybackActions = PlaybackActions.DEFAULT

  /**
   * Note: [resume] probably only true for casting (not this Queue)
   */
  override suspend fun activate(resume: Boolean, playNow: PlayNow) {
    prefs = queuePrefsSingleton.instance()
    reactToShuffleChanges()
    reactToRepeatModeChanges()
    sessionState.contentType = AudioFocusManager.ContentType.Audio
    sessionState.setQueueTitle(fetch(R.string.LocalAudio))
    queueState = when (val result = queueStateDao.getState()) {
      is Ok -> result.value
      is Err -> QueueState.INACTIVE_QUEUE_STATE.also {
        LOG.e { it("Can't read queue state %s", result.error) }
      }
    }
    scrobbler = ScrobblerFacade(appPrefs, scrobblerFactory)
    upNextQueue = itemFactory.makeUpNextQueue(false)
    LOG._e { it("activate upNextQueue.size=%d", upNextQueue.size) }
    upNextShuffled = itemFactory.makeUpNextQueue(true)
    val currentQueue = queue
    setSessionQueue(currentQueue, sessionState)
    if (currentQueue.isNotEmpty()) {
      var startingIndex = 0
      var startingPosition: Millis = Millis.ZERO
      if (queueState.queueIndex in currentQueue.indices) {
        startingIndex = queueState.queueIndex
        startingPosition = queueState.playbackPosition
      }
      var item = try {
        currentQueue[startingIndex]
      } catch (e: Throwable) {
        NullPlayableAudioItem
      }
      if (!item.isValid || item.id != queueState.mediaId) {
        item = currentQueue[0]
        updateQueueState(QueueState(item.id, 0, Millis.ZERO))
      }
      if (!resume) {
        transitionToNext(
          NullPlayableAudioItem,
          item,
          if (playNow()) DirectTransition() else NoOpMediaTransition,
          playNow,
          startingPosition
        )
      }
    } else {
      isActive.value = true
    }
  }

  private fun setSessionQueue(queue: List<PlayableAudioItem>, sessionState: MediaSessionState) {
    scope.launch {
      logExecTime({ time -> LOG._e { it("sessionState.setQueue %d", time) } }) {
        sessionState.setQueue { queue.toCompat() }
      }
    }
  }

  private fun reactToRepeatModeChanges() {
    prefs.repeatMode
      .asFlow()
      .onEach { mode -> sessionState.setRepeat(mode) }
      .launchIn(scope)
  }

  private fun reactToShuffleChanges() {
    prefs.shuffleMode
      .asFlow()
      .onEach { mode -> sessionState.setShuffle(mode) }
      .launchIn(scope)
  }

  override fun deactivate() {
    scope.cancel()
    queueState = QueueState.INACTIVE_QUEUE_STATE
  }

  private fun getItemFromIndex(index: Int): PlayableAudioItem =
    if (index in queue.indices) queue[index] else NullPlayableAudioItem

  override suspend fun getNextMediaTitle(): Title = if (isActive.value) {
    val item = nextItem
    when {
      nextItem.isValid -> item.title
      queue.isNotEmpty() -> getNextListFirstMediaTitle()
      else -> Title.EMPTY
    }
  } else Title.EMPTY

  private suspend fun getNextListFirstMediaTitle(): Title = try {
    val nextList = getNextList(prefs.lastListType(), prefs.lastListName())
    if (nextList.idList.isNotEmpty()) {
      when (val result = audioMediaDao.getMediaTitle(nextList.idList[0])) {
        is Ok -> result.value
        is Err -> Title.EMPTY
      }
    } else Title.EMPTY
  } catch (e: Exception) {
    LOG.e(e) { it("Error getting next list, first title") }
    Title.EMPTY
  }

  override suspend fun play(immediate: Boolean) {
    LOG._e { it("play immediate=%s", immediate) }
    currentItem.play(immediate)
  }

  override suspend fun pause(immediate: Boolean) {
    LOG._e { it("pause immediate=%s", immediate) }
    currentItem.pause(immediate)
  }

  override suspend fun stop() {
    LOG._e { it("stop") }
    currentItem.stop()
  }

  //  override fun togglePlayPause() {
//    TODO("Not yet implemented")
//  }

  override suspend fun next() {
    nextSong(PlayNow(currentItem.isPlaying), manualTransition)
  }

  override suspend fun previous() {
    if (queueContainsMedia()) {
      if (appPrefs.rewindThenPrevious() && currentItem.position >= SEEK_TO_ZERO_POSITION) {
        currentItem.seekTo(Millis(0))
      } else {
        val newIndex = queueState.queueIndex - 1
        if (newIndex < 0) {
          // start of queue
        } else {
          doGoToIndex(newIndex, PlayNow(currentItem.isPlaying), manualTransition)
        }
      }
    }
  }

  override suspend fun seekTo(position: Millis) {
    currentItem.seekTo(position)
  }

  override suspend fun fastForward() {
    val position = currentItem.position
    val end = currentItem.duration - Millis.FIVE_SECONDS
    if (position < end) currentItem.seekTo((position + Millis.TEN_SECONDS).coerceAtMost(end))
  }

  override suspend fun rewind() {
    currentItem.seekTo((currentItem.position - Millis.TEN_SECONDS).coerceAtLeast(Millis.ZERO))
  }

  override suspend fun goToIndexMaybePlay(index: Int) {
    doGoToIndex(index, PlayNow(currentItem.isPlaying), manualTransition)
  }

  override suspend fun duck() {
    when (appPrefs.duckAction()) {
      DuckAction.Duck -> currentItem.volume = appPrefs.duckVolume()
      DuckAction.Pause -> pause(immediate = true)
      DuckAction.DoNothing -> {
      }
    }
  }

  override suspend fun endDuck() {
    when (appPrefs.duckAction()) {
      DuckAction.Duck -> currentItem.volume = Volume.MAX
      DuckAction.Pause -> play(immediate = true)
      DuckAction.DoNothing -> {
      }
    }
  }

  override suspend fun addToUpNext(audioIdList: AudioIdList) {
    LOG._e { it("addToUpNext size=%d", audioIdList.size) }
    if (upNextQueue.isEmpty()) {
      playNext(audioIdList, ClearQueue(true), PlayNow(false), transition = manualTransition)
    } else {
      withContext(MAIN_WITH_HANDLER) {
        retryQueueChange {
          val prevCurrentIndex = currentItemIndex
          val prevCurrent = currentItem
          val idList = makeIdListLessCurrent(audioIdList, prevCurrent)

          if (idList.isNotEmpty()) {
            val (newQueue, newShuffled, newIndex) = addItemsToQueues(
              upNextQueue,
              upNextShuffled,
              prevCurrent,
              prevCurrentIndex,
              idList,
              shuffleMode,
              AddAt.AtEnd,
              clearAndRemakeQueues = false
            )
            persistIfCurrentUnchanged(newQueue, newShuffled, prevCurrent, prevCurrentIndex)
            establishNewQueues(newQueue, newShuffled, newIndex)
          }
        }
      }
    }
  }

  override suspend fun prepareNext(audioIdList: AudioIdList) {
    playNext(audioIdList, ClearQueue(false), PlayNow(false), manualTransition)
  }

  override suspend fun playNext(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transition: PlayerTransitionPair
  ) {
    // We will only swap the up next queue on the UI thread, which means we need to save current
    // information and only swap if the new queue "current" matches the old. If doesn't match the
    // current item changed while we were creating the new queue, need to loop and try again.
    // Retry because the queue changed should be very rare (such as auto advance while playing)
    withContext(MAIN_WITH_HANDLER) {
      retryQueueChange {
        val prevCurrentIndex = currentItemIndex
        val prevCurrent = currentItem
        val firstIsCurrent = audioIdList.isNotEmpty && audioIdList.idList[0] == prevCurrent.id
        val idList = makeIdListLessCurrent(audioIdList, prevCurrent)

        if (idList.isNotEmpty()) {
          val upNextEmptyOrCleared = upNextQueue.isEmpty() || clearUpNext() || !prevCurrent.isValid
          val prevLast = getLastQueueItem()

          val (newQueue, newShuffled, newIndex) = addItemsToQueues(
            upNextQueue,
            upNextShuffled,
            prevCurrent,
            prevCurrentIndex,
            idList,
            shuffleMode,
            AddAt.AfterCurrent,
            upNextEmptyOrCleared
          )

          persistIfCurrentUnchanged(
            newQueue,
            newShuffled,
            prevCurrent,
            prevCurrentIndex,
            skipUnchangedCheck = upNextEmptyOrCleared
          )

          establishNewQueues(
            newQueue,
            newShuffled,
            newIndex,
            upNextEmptyOrCleared
          )

          if (upNextEmptyOrCleared || lastQueueItemIsNot(prevLast)) {
            setLastList(audioIdList)
          }

          if (upNextEmptyOrCleared || firstIsCurrent) {
            transitionToNext(prevCurrent, currentItem, transition, playNow, Millis.ZERO)
          } else if (playNow()) {
            nextSong(PlayNow(true), transition)
          }
        } else if (firstIsCurrent) {
          transitionToNext(prevCurrent, currentItem, transition, playNow, Millis.ZERO)
        }
      }
    }
  }

  override suspend fun goToQueueItem(instanceId: Long) {
    val theQueue = queue
    val index = theQueue.indexOfFirst { it.instanceId == instanceId }
    if (index in theQueue.indices) goToIndexMaybePlay(index)
  }

  private fun queueContainsMedia(): Boolean = queue.isNotEmpty()

  private fun atEndOfQueue(): Boolean = currentItemIndex >= queue.size - 1

  private suspend fun nextSong(
    playNow: PlayNow,
    transitionPair: PlayerTransitionPair
  ): Boolean {
    LOG._e { it("nextSong") }
    var success = false
    if (queueContainsMedia()) {
      if (!atEndOfQueue()) {
        LOG._e { it("not at end of queue") }
        doGoToIndex(currentItemIndex + 1, playNow, transitionPair)
        success = true
      } else {
        if (RepeatMode.All == prefs.repeatMode()) {
          LOG._e { it("repeat queue") }
          doGoToIndex(0, playNow, transitionPair)
          success = true
        } else {
          val endOfQueueAction = appPrefs.endOfQueueAction()
          LOG._e { it("end of queue action=%s", endOfQueueAction) }
          if (endOfQueueAction.shouldGoToNextList) {
            // determine next list and play it, clearing the queue
            val currentListType = prefs.lastListType()
            val currentListName = prefs.lastListName()
            val nextList = getNextList(currentListType, currentListName)
            LOG._e { it("nextList=%s", nextList) }
            if (nextList.isNotEmpty) {
              val shouldShuffle = currentListType != nextList.listType &&
                currentListName != nextList.listName &&
                endOfQueueAction.shouldShuffle
              playNext(
                if (shouldShuffle) nextList.shuffled() else nextList,
                ClearQueue(true),
                playNow
              )
              success = true
            }
          }
        }
      }
    }
//    if (!success) getNotifier().postEndOfQueue() TODO
    return success
  }

  private suspend fun getNextList(
    currentListType: SongListType,
    currentListName: String
  ): AudioIdList {
    LOG._e { it("getNextList currentList=%s name=%s", currentListType, currentListName) }
    val countResult = audioMediaDao.getCountAllAudio()
    return if (countResult is Ok && countResult.value > 0) {
      var nextList = currentListType.getNextList(audioMediaDao, currentListName, appPrefs)
      while (nextList.isEmpty) {
        nextList = nextList.listType.getNextList(audioMediaDao, nextList.listName, appPrefs)
        if (currentListType == nextList.listType && currentListName == nextList.listName) {
          // looped around and didn't find anything, quit and return an empty list
          nextList = nextList.copy(idList = MediaIdList.EMPTY_LIST)
          break
        }
      }
      nextList
    } else {
      AudioIdList(MediaIdList.EMPTY_LIST, SongListType.All, "")
    }
  }

  private suspend fun doGoToIndex(
    goToIndex: Int,
    ensureSongPlays: PlayNow,
    transition: PlayerTransitionPair
  ) {
    debug { ensureUiThread() }
    LOG._e { it("doGoToIndex goTo:%d", goToIndex) }
    val currentIndex = currentItemIndex
    val current = currentItem

    var nextItem: PlayableAudioItem = NullPlayableAudioItem
    var checkIfSkipped = false
    val activeQueueList = queue
    if (activeQueueList.isEmpty() || goToIndex < 0 || goToIndex >= activeQueueList.size) {
      LOG.e {
        it("go to index out of bounds index=%d queueSize=%d", goToIndex, activeQueueList.size)
      }
      return
    }

    if (current.isValid && goToIndex > currentIndex) {
      checkIfSkipped = true
    }

    if (goToIndex in activeQueueList.indices) {
      if (goToIndex == currentIndex && current.isValid) {
        nextItem = current.cloneItem()
        replaceCurrentItemInQueue(currentIndex, current, nextItem)
      } else {
        nextItem = activeQueueList[goToIndex]
        if (nextItem.isValid) {
          updateQueueState(QueueState(nextItem.id, goToIndex, Millis.ZERO))
        }
      }
    }
    if (nextItem.isValid) {
      if (checkIfSkipped) current.checkMarkSkipped()
      transitionToNext(current, nextItem, transition, ensureSongPlays, Millis.ZERO)
    } else {
      updateMetadata()
    }
  }

  private fun replaceCurrentItemInQueue(
    index: Int,
    item: PlayableAudioItem,
    replacement: PlayableAudioItem
  ) {
    if (shuffleMedia) {
      debugCheck(upNextShuffled[index].instanceId == item.instanceId) {
        "index does not match item"
      }
      upNextShuffled = upNextShuffled.mapIndexed { i, itemAtIndex ->
        if (i == index) replacement else itemAtIndex
      }
      upNextQueue = upNextQueue.map { queueItem ->
        if (queueItem.instanceId == item.instanceId) replacement else queueItem
      }
    } else {
      debugCheck(upNextQueue[index].instanceId == item.instanceId) { "index does not match item" }
      upNextQueue.mapIndexed { i, itemAtIndex ->
        if (i == index) replacement else itemAtIndex
      }
    }
  }

  private suspend fun transitionToNext(
    currentItem: PlayableAudioItem,
    nextItem: PlayableAudioItem,
    transition: PlayerTransitionPair,
    playNow: PlayNow,
    position: Millis
  ) {
    if (currentItem.isValid) {
      currentItemFlowJob?.cancel()
      currentItemFlowJob = null
      scrobbler.complete(currentItem)
    }
    val exitTransition = transition.exitTransition
    if (!playNow()) {
      currentItem.shutdown()
    } else {
      currentItem.shutdown(exitTransition)
    }
    currentItemFlowJob = nextItem.collectEvents {
      nextItem.prepareSeekMaybePlay(
        position,
        if (playNow()) transition.enterTransition else NoOpPlayerTransition,
        playNow,
      )
    }
  }

  private fun PlayableAudioItem.collectEvents(doOnStart: suspend () -> Unit) = scope.launch {
    eventFlow
      .onStart { doOnStart() }
      .onEach { event -> handleAudioItemEvent(event) }
      .catch { cause -> LOG.e(cause) { it("%s%s event handled exception", id, title) } }
      .onCompletion { cause -> LOG._i(cause) { it("%s%s event flow completed", id, title) } }
      .collect()
  }

  private suspend fun handleAudioItemEvent(event: PlayableAudioItemEvent) {
//    LOG._e { it("handleAudioItemEvent %s", event) }
    when (event) {
      is PlayableAudioItemEvent.Prepared -> onPrepared(event)
      is PlayableAudioItemEvent.PositionUpdate -> onPositionUpdate(event)
      is PlayableAudioItemEvent.Start -> onStart(event)
      is PlayableAudioItemEvent.Paused -> onPaused(event)
      is PlayableAudioItemEvent.Stopped -> onStopped(event)
      is PlayableAudioItemEvent.PlaybackComplete -> onPlaybackCompleted(event)
      is PlayableAudioItemEvent.Error -> onError(event)
      is PlayableAudioItemEvent.None -> {
      }
    }
  }

  private suspend fun onPrepared(event: PlayableAudioItemEvent.Prepared) {
    isActive.value = true
    val item = event.audioItem
    val duration = event.duration
    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = event.currentPosition))
    updateMetadata()
    updatePlaybackState(
      item,
      currentItemIndex,
      queue,
      if (item.isPlaying) PlayState.Playing else PlayState.Paused
    )
    scope.launch(MAIN_WITH_HANDLER) {
      val nextTitle = getNextMediaTitle()
//      getNotifier().postNextMediaTitleChanged(NextMediaTitleChangedEvent(nextTitle)) TODO
    }
  }

  private suspend fun onPositionUpdate(event: PlayableAudioItemEvent.PositionUpdate) {
    val item = event.audioItem
    val position = event.currentPosition
    val duration = event.duration
    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = event.currentPosition))
    if (item.isPlaying) {
      if (item.supportsFade && shouldAutoAdvanceFrom(position, duration)) {
        doNextOrRepeat()
      } else {
        updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Playing)
      }
    } else {
      updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Paused)
    }
  }

  private suspend fun onStart(event: PlayableAudioItemEvent.Start) {
    playState = PlayState.Playing
    val item = event.audioItem
    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = event.currentPosition))
    //    getNotifier().onStart(item, firstStart) TODO
    if (event.firstStart) {
      scrobbler.start(item)
    } else {
      scrobbler.resume(item)
    }
    updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Playing)
  }

  private suspend fun onPaused(event: PlayableAudioItemEvent.Paused) {
    playState = PlayState.Paused
    val item = event.audioItem
    val position = event.currentPosition
    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = position))
//    getNotifier().onPaused(item, position) TODO
    scrobbler.pause(item)
    updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Paused)
  }

  private suspend fun onStopped(event: PlayableAudioItemEvent.Stopped) {
    val item = event.audioItem
    if (currentItem.instanceId == item.instanceId) {
      playState = PlayState.Stopped
//    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = event.currentPosition))
      updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Stopped)
    }
  }

  private suspend fun onPlaybackCompleted(event: PlayableAudioItemEvent.PlaybackComplete) {
    val item = event.audioItem
    if (currentItem.instanceId == item.instanceId) {
      if (!doNextOrRepeat()) {
        playState = PlayState.Paused
        updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Paused)
      }
    }
  }

  private suspend fun onError(event: PlayableAudioItemEvent.Error) {
    val item = event.audioItem
    LOG.e { it("Playback error for item %s, %s", item.title, item.albumArtist) }
    nextSong(PlayNow(true), manualTransition)
//    getNotifier().onError(item) TODO
  }

  private suspend fun updatePlaybackState(
    item: PlayableAudioItem,
    index: Int,
    queue: List<PlayableAudioItem>,
    playState: PlayState
  ) {
    sessionState.setState(
      PlaybackState(
        playState,
        item.position,
        PlaybackRate.NORMAL,
        item.instanceId,
        PlaybackActions(
          hasMedia = queue.isNotEmpty(),
          isPlaying = item.isPlaying,
          hasPrev = index > 0,
          hasNext = index < queue.size - 1
        )
      )
    )
  }

  private suspend fun updateMetadata() {
    val item = currentItem
    sessionState.setMetadata(
      Metadata(
        item.id,
        item.title,
        item.albumTitle,
        item.albumArtist,
        item.artist,
        item.duration,
        item.trackNumber,
        item.localAlbumArt,
        item.albumArt,
        item.rating,
        item.location
      )
    )
  }

  private fun updateQueueState(newState: QueueState) {
    queueState = queueStateDao.persistState(newState)
  }

  private suspend fun doNextOrRepeat(): Boolean = if (prefs.repeatMode().current) {
    doGoToIndex(currentItemIndex, PlayNow(true), autoAdvanceTransition)
    true
  } else {
    nextSong(PlayNow(true), autoAdvanceTransition)
  }

  private fun shouldAutoAdvanceFrom(position: Millis, duration: Millis): Boolean {
    if (!atEndOfQueue() && appPrefs.autoAdvanceFade()) {
      val fadeLength = appPrefs.autoAdvanceFadeLength()
      if (duration > fadeLength && duration - position < fadeLength + OFFSET_CONSIDERED_END) {
        return true
      }
    }
    return false
  }

  private fun makeIdListLessCurrent(
    audioIdList: AudioIdList,
    prevCurrent: PlayableAudioItem
  ): LongList = if (audioIdList.size == 1 && audioIdList.list.single() == prevCurrent.id.value) {
    LongLists.EMPTY_LIST
  } else {
    audioIdList.list.apply { if (size > 1) rem(prevCurrent.id.value) }
  }

  /**
   * Get the last item of the active queue or [NullPlayableAudioItem] if the list is
   * empty
   */
  private fun getLastQueueItem() = queue.lastOrNull() ?: NullPlayableAudioItem

  private fun lastQueueItemIsNot(item: PlayableAudioItem) =
    getLastQueueItem().instanceId != item.instanceId

  private fun establishNewQueues(
    newQueue: QueueList,
    newShuffled: QueueList,
    newIndex: Int,
    clearUpNext: Boolean = false
  ) {
    upNextQueue = newQueue
    upNextShuffled = newShuffled
    val activeQueue = queue
    LOG._e { it("establishNewQueues activeQueue.size=%d", activeQueue.size) }
    setSessionQueue(activeQueue, sessionState)
    if (activeQueue.isNotEmpty()) {
      if (clearUpNext) {
        updateQueueState(QueueState(activeQueue[0].id, 0, Millis.ZERO))
      } else {
        val item = activeQueue[newIndex]
        updateQueueState(QueueState(item.id, newIndex, item.position))
      }
    } else queueStateDao.persistState(QueueState.INACTIVE_QUEUE_STATE)
//    postUpNextChangedEvent()  TODO
  }

  private suspend fun setLastList(audioIdList: AudioIdList) {
    prefs.setLastList(audioIdList)
  }

  private suspend fun addItemsToQueues(
    currentQueue: QueueList,
    currentShuffled: QueueList,
    prevCurrent: PlayableAudioItem,
    prevCurrentIndex: Int,
    idList: LongList,
    shuffleMode: ShuffleMode,
    addAt: AddAt,
    clearAndRemakeQueues: Boolean
  ): Triple<QueueList, QueueList, Int> = withContext(Dispatchers.Default) {
    val newQueue: QueueList
    val newShuffled: QueueList
    val newIndex: Int
    val allowDuplicates = appPrefs.allowDuplicates()
    val newQueueItems = itemFactory.makeNewQueueItems(idList)

    if (clearAndRemakeQueues) {
      // if the queue is empty OR we are supposed to clear the queue, just build new list(s)
      newQueue = newQueueItems
      newShuffled = maybeMakeShuffled(this@LocalAudioQueueImpl.shuffleMode, newQueue)
      newIndex = 0
    } else {
      val idSet = LongOpenHashSet(idList)
      if (shuffleMode.shuffleMedia) {
        val queueInfo = currentShuffled.addNewItems(
          newQueueItems,
          prevCurrentIndex,
          prevCurrent,
          idSet,
          allowDuplicates,
          addAt,
          shuffleMode
        )
        newShuffled = queueInfo.queue
        newIndex = queueInfo.index
        newQueue = currentQueue.filterIfNoDuplicatesAllowed(allowDuplicates, idSet)
          .apply { addAll(newQueueItems) }
      } else {
        val queueInfo = currentQueue.addNewItems(
          newQueueItems,
          prevCurrentIndex,
          prevCurrent,
          idSet,
          allowDuplicates,
          addAt,
          shuffleMode
        )
        newQueue = queueInfo.queue
        newIndex = queueInfo.index
        newShuffled = mutableListOf()
      }
    }
    Triple(newQueue, newShuffled, newIndex)
  }

  private fun maybeMakeShuffled(shuffleMode: ShuffleMode, newQueue: QueueList): QueueList =
    if (shuffleMode.shuffleMedia) newQueue.shuffled() else emptyList()

  private suspend fun persistIfCurrentUnchanged(
    newQueue: QueueList,
    newShuffled: QueueList,
    prevCurrent: PlayableAudioItem,
    prevCurrentIndex: Int,
    skipUnchangedCheck: Boolean = false
  ) {
    if (skipUnchangedCheck) {
      queueDao.replaceQueueItems(AudioMediaDao.QUEUE_ID, newQueue, newShuffled)
    } else if (currentItemIsStill(prevCurrent, prevCurrentIndex)) {
      queueDao.replaceQueueItems(AudioMediaDao.QUEUE_ID, newQueue, newShuffled)
      if (!currentItemIsStill(prevCurrent, prevCurrentIndex)) {
        queueDao.replaceQueueItems(AudioMediaDao.QUEUE_ID, upNextQueue, upNextShuffled) // rollback
        throw QueueChangedException("Changed after persisting")
      }
    } else throw QueueChangedException("Changed before persisting")
  }

  private fun currentItemIsStill(prevCurrent: PlayableAudioItem, newIndex: Int): Boolean =
    prevCurrent.isNotValid || queue.itemIsAtIndex(prevCurrent, newIndex)

  class QueueChangedException(message: String) : Exception(message)

  /**
   * From Roman Elizarov https://stackoverflow.com/a/46890009/2660904
   */
  private suspend fun <T> retryQueueChange(
    times: Int = MAX_QUEUE_RETRIES,
    initialDelay: Millis = Millis.ONE_HUNDRED,
    maxDelay: Millis = Millis.ONE_SECOND,
    factor: Long = 2,
    block: suspend () -> T
  ): T {
    var currentDelay = initialDelay
    repeat(times - 1) { index ->
      try {
        return block()
      } catch (e: QueueChangedException) {
        LOG.e(e) { it("Queue changed during update. Retry=$index") }
      }
      delay(currentDelay())
      currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)
    }
    return block()
  }
}

private fun List<PlayableAudioItem>.itemIsAtIndex(
  item: PlayableAudioItem,
  index: Int
): Boolean = if (index in indices) this[index].instanceId == item.instanceId else false

/**
 * Add [newQueueItems] into this list after [currentIndex] or at the end of the list depending on
 * [addAt], removing all items being added from queue history if ![allowDuplicates] using
 * [newItemsIdSet], and adjusting the index as necessary. If [shuffleMode].shuffleMedia then the new
 * items will be shuffled into the existing items in the list after [currentIndex].
 *
 * @return a [QueueInfo] with the new list and index after adding the new items
 */
fun QueueList.addNewItems(
  newQueueItems: QueueList,
  currentIndex: Int,
  currentItem: PlayableAudioItem,
  newItemsIdSet: LongSet,
  allowDuplicates: AllowDuplicates,
  addAt: AddAt,
  shuffleMode: ShuffleMode,
  shuffler: ListShuffler = LIST_SHUFFLER
): QueueInfo = if (newQueueItems.isEmpty()) QueueInfo(this, currentIndex) else {
  val beforeCurrent = sublistFromStartTo(currentIndex)
  val queueStart = beforeCurrent.filterIfNoDuplicatesAllowed(allowDuplicates, newItemsIdSet)
  val afterCurrent = sublistToEndFrom(currentIndex + 1)
    .filterIfNoDuplicatesAllowed(allowDuplicates, newItemsIdSet)

  Pair(
    ArrayList<PlayableAudioItem>(queueStart.size + afterCurrent.size + 1).apply {
      addAll(queueStart)
      if (currentItem != NullPlayableAudioItem) add(currentItem)
      addAt.addTo(this, afterCurrent, newQueueItems, shuffleMode, shuffler)
    },
    if (currentIndex < 0) 0 else currentIndex - (beforeCurrent.size - queueStart.size)
  )
}

private fun List<PlayableAudioItem>.filterIfNoDuplicatesAllowed(
  allowDuplicates: AllowDuplicates,
  idsToRemove: LongCollection
): MutableList<PlayableAudioItem> {
  return if (!allowDuplicates()) {
    asSequence()
      .filterNot { idsToRemove.contains(it.id.value) }
      .toCollection(ArrayList(size))
  } else {
    toMutableList()
  }
}

/**
 * Create a sublist from index 0 to [toIndex] exclusive. If this list is empty or [toIndex] is not
 * within 0..[List.size], an empty list is returned.
 */
fun <E> List<E>.sublistFromStartTo(toIndex: Int): List<E> =
  if (isNotEmpty() && toIndex in 0..size) subList(0, toIndex) else emptyList()

/**
 * Create a sublist from [fromIndex] to the end of the list. If the list is empty or [fromIndex] is
 * not within 1..[List.size], an empty list is returned.
 */
fun <E> List<E>.sublistToEndFrom(fromIndex: Int): List<E> =
  if (isNotEmpty() && fromIndex in 1..size) subList(fromIndex, size) else emptyList()

private fun MutableList<PlayableAudioItem>.removeWithInstanceId(queueMediaItem: PlayableAudioItem) {
  removeAt(indexOfFirst { it.instanceId == queueMediaItem.instanceId })
}

private fun List<PlayableAudioItem>.toCompat(): QueueItemList = QueueItemArrayList(size).apply {
  this@toCompat.forEach { audioItem ->
    add(MediaSessionCompat.QueueItem(audioItem.toDescriptionCompat(), audioItem.instanceId))
  }
}

private fun PlayableAudioItem.toDescriptionCompat() = MediaDescriptionCompat.Builder()
  .setMediaId(id.toString())
  .setTitle(title())
  .setSubtitle(artist.value)
  .setDescription(albumTitle.value)
  .setIconUri(if (localAlbumArt !== Uri.EMPTY) localAlbumArt else albumArt)
  .setMediaUri(location)
  .setExtras(
    Bundle().apply {
      putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration())
      putParcelable(MediaMetadataCompat.METADATA_KEY_RATING, rating.toStarRating().toCompat())
    }
  )
  .build()
