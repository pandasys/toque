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
import android.telephony.TelephonyManager
import androidx.datastore.preferences.core.Preferences
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.R
import com.ealva.toque.app.Toque
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.audioout.AudioOutputState
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.Title
import com.ealva.toque.common.debug
import com.ealva.toque.common.debugCheck
import com.ealva.toque.common.ensureUiThread
import com.ealva.toque.common.fetch
import com.ealva.toque.db.AudioIdList
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.QueueDao
import com.ealva.toque.db.QueuePositionState
import com.ealva.toque.db.QueuePositionStateDao
import com.ealva.toque.db.QueuePositionStateDaoFactory
import com.ealva.toque.db.SongListType
import com.ealva.toque.log._e
import com.ealva.toque.log._i
import com.ealva.toque.persist.MediaIdList
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.media.StarRating
import com.ealva.toque.service.media.toRating
import com.ealva.toque.service.player.AvPlayer
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
import com.ealva.toque.service.session.common.PlaybackActions
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.server.AudioFocusManager
import com.ealva.toque.service.session.server.AudioFocusManager.FocusReaction
import com.ealva.toque.service.session.server.MediaSessionControl
import com.ealva.toque.service.session.server.MediaSessionState
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.LibVlcSingleton
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

typealias QueueList = List<PlayableAudioItem>

data class LocalAudioQueueState(
  val queue: List<AudioItem>,
  val queueIndex: Int,
  val position: Millis,
  val duration: Millis,
  val playingState: PlayState,
  val repeatMode: RepeatMode,
  val shuffleMode: ShuffleMode,
  val eqMode: EqMode,
  val currentPreset: EqPreset,
  val extraMediaInfo: String,
  val playbackRate: PlaybackRate = PlaybackRate.NORMAL
) {
  override fun toString(): String {
    return """
      |LocalAudioQueueState(
      |  queue.size=${queue.size},
      |  index=$queueIndex,
      |  position=$position,
      |  duration=$duration,
      |  playState=$playingState,
      |  repeat=$repeatMode,
      |  shuffle=$shuffleMode,
      |)
    """.trimMargin()
  }

  companion object {
    val NONE = LocalAudioQueueState(
      queue = emptyList(),
      queueIndex = -1,
      position = Millis(0),
      duration = Millis(0),
      playingState = PlayState.Stopped,
      repeatMode = RepeatMode.None,
      shuffleMode = ShuffleMode.None,
      eqMode = EqMode.Off,
      currentPreset = EqPreset.NONE,
      extraMediaInfo = ""
    )
  }
}

private val LOG by lazyLogger(LocalAudioQueue::class)
private const val MAX_QUEUE_RETRIES = 5

typealias QueueInfo = Pair<QueueList, Int>

inline val QueueInfo.queue: QueueList get() = first
inline val QueueInfo.index: Int get() = second

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
  val queueState: StateFlow<LocalAudioQueueState>

  override val queueType: QueueType get() = QueueType.Audio

  val seeking: Boolean

  val manualTransition: PlayerTransitionPair
  val autoAdvanceTransition: PlayerTransitionPair

  fun toggleEqMode()
  fun setRating(rating: StarRating, allowFileUpdate: Boolean = false)

  suspend fun addToUpNext(audioIdList: AudioIdList)

  suspend fun playNext(
    audioIdList: AudioIdList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transition: PlayerTransitionPair = manualTransition
  )

  fun goToQueueItem(instanceId: Long)
  suspend fun prepareNext(audioIdList: AudioIdList)
  fun nextRepeatMode()
  fun nextShuffleMode()
  /** This method is necessary for MediaSession callback */
  fun setRepeatMode(mode: RepeatMode)
  /** This method is necessary for MediaSession callback */
  fun setShuffleMode(mode: ShuffleMode)

  companion object {
    suspend fun make(
      sessionControl: MediaSessionControl,
      sessionState: MediaSessionState,
      queuePositionStateDaoFactory: QueuePositionStateDaoFactory,
      playableAudioItemFactory: PlayableAudioItemFactory,
      audioManager: AudioManager,
      appPrefsSingleton: AppPrefsSingleton,
      libVlcPrefsSingleton: LibVlcPrefsSingleton
    ): LocalAudioQueue {
      val appPrefs = appPrefsSingleton.instance()
      val libVlcPrefs = libVlcPrefsSingleton.instance()
      val dao = queuePositionStateDaoFactory.makeStateDao(AudioMediaDao.QUEUE_ID)
      val positionState = when (val result = dao.getState()) {
        is Ok -> result.value
        is Err -> QueuePositionState.INACTIVE_QUEUE_STATE.also {
          LOG.e { it("Can't read queue state %s", result.error) }
        }
      }
      return LocalAudioCommandProcessor(
        LocalAudioQueueImpl(
          queuePrefsSingleton.instance(),
          sessionState,
          positionState,
          dao,
          playableAudioItemFactory,
          MusicStreamVolume(audioManager),
          appPrefs,
          libVlcPrefs
        ),
        appPrefs,
        sessionControl
      )
    }
  }
}

@Suppress("SameParameterValue", "LargeClass")
private class LocalAudioQueueImpl(
  private val prefs: QueuePrefs,
  private val sessionState: MediaSessionState,
  initialState: QueuePositionState,
  private val queuePositionStateDao: QueuePositionStateDao,
  private val itemFactory: PlayableAudioItemFactory,
  override val streamVolume: StreamVolume,
  private val appPrefs: AppPrefs,
  private val libVlcPrefs: LibVlcPrefs
) : LocalAudioQueue, KoinComponent {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val audioMediaDao: AudioMediaDao by inject()
  private val queueDao: QueueDao by inject()
  private val eqPresetFactory: EqPresetFactory by inject()
  private val audioOutputState: AudioOutputState by inject()
  private var sharedPlayerState: SharedPlayerState = NullSharedPlayerState
  private val scrobblerFactory: ScrobblerFactory by inject()
  private val audioFocusManager: AudioFocusManager by inject()
  private val telephonyManager: TelephonyManager by inject()
  private val libVlcSingleton: LibVlcSingleton by inject()
  private var scrobbler: Scrobbler = NullScrobbler
  private var currentItemFlowJob: Job? = null
  private var playState: PlayState = PlayState.None
  private var positionState = initialState
  private var lastShuffleMode = prefs.shuffleMode()

  override val queueState = MutableStateFlow(makeInitialState())

  private val focusRequest = AvPlayer.FocusRequest {
    LOG._e { it("focusRequest") }
    audioFocusManager.requestFocus(AudioFocusManager.ContentType.Audio) { playState.isPlaying }
  }

  private fun makeInitialState(): LocalAudioQueueState {
    return LocalAudioQueueState(
      emptyList(),
      -1,
      Millis(0),
      Millis(0),
      PlayState.None,
      prefs.repeatMode(),
      prefs.shuffleMode(),
      sharedPlayerState.eqMode.value,
      EqPreset.NONE,
      ""
    )
  }

  override var seeking = false
  private val repeat: RepeatMode get() = prefs.repeatMode()

  override fun nextRepeatMode() = setRepeatMode(repeat.getNext())

  /** Set RepeatMode in prefs but UI is updated listening to [QueuePrefs.repeatMode] flow */
  override fun setRepeatMode(mode: RepeatMode) {
    scope.launch { prefs.repeatMode.set(mode) }
  }

  val shuffle: ShuffleMode get() = prefs.shuffleMode()
  override fun nextShuffleMode() = setShuffleMode(shuffle.getNext())

  /** Set ShuffleMode in prefs but UI is updated listening to [QueuePrefs.shuffleMode] */
  override fun setShuffleMode(mode: ShuffleMode) {
    scope.launch { prefs.shuffleMode.set(mode) }
  }

  override fun setRating(rating: StarRating, allowFileUpdate: Boolean) {
    currentItem.setRating(rating.toRating(), allowFileUpdate)
    updateMetadata()
  }

  /**
   * Toggle EqMode but UI is updated listening to [SharedPlayerState.eqMode], which is already
   * a StateFlow (compared to [QueuePrefs.eqMode]
   */
  override fun toggleEqMode() {
    scope.launch { prefs.eqMode.set(prefs.eqMode().next()) }
  }

  private val queue: List<PlayableAudioItem>
    get() = if (shuffle.shuffleMedia) upNextShuffled else upNextQueue

  private val currentItem: PlayableAudioItem get() = getItemFromIndex(currentItemIndex)
  private val currentItemIndex: Int get() = positionState.queueIndex
  private val nextItem: PlayableAudioItem get() = getItemFromIndex(currentItemIndex + 1)
  private var upNextQueue: QueueList = mutableListOf()
  private var upNextShuffled: QueueList = mutableListOf()

  override val manualTransition: PlayerTransitionPair
    get() = if (appPrefs.manualChangeFade()) {
      CrossFadeTransition(
        appPrefs.manualChangeFadeLength(),
        libVlcPrefs.audioOutputModule().forceStartVolumeZero
      )
    } else {
      DirectTransition()
    }

  override val autoAdvanceTransition: PlayerTransitionPair
    get() = if (appPrefs.autoAdvanceFade()) {
      CrossFadeTransition(
        appPrefs.autoAdvanceFadeLength(),
        libVlcPrefs.audioOutputModule().forceStartVolumeZero
      )
    } else {
      DirectTransition()
    }

  override val isActive = MutableStateFlow(false)

  /**
   * Note: [resume] probably only true for casting (not this Queue)
   */
  override suspend fun activate(resume: Boolean, playNow: PlayNow) {
    sharedPlayerState = SharedPlayerState(
      scope,
      eqPresetFactory,
      prefs.eqMode.asFlow().stateIn(scope),
      audioOutputState.output,
      prefs.playbackRate.asFlow().stateIn(scope),
      libVlcPrefs.audioOutputModule.asFlow().stateIn(scope)
    )
    reactToEqModeChanges()
    reactToHeadsetChanges()
    reactToAudioFocusChanges()
    reactToShuffleChanges()
    reactToRepeatModeChanges()
    reactToLibVlcPrefs()
    sessionState.contentType = AudioFocusManager.ContentType.Audio
    sessionState.setQueueTitle(fetch(R.string.LocalAudio))
    scrobbler = ScrobblerFacade(appPrefs, scrobblerFactory)
    upNextQueue = itemFactory.makeUpNextQueue(false, focusRequest, sharedPlayerState)
    upNextShuffled = itemFactory.makeUpNextQueue(true, focusRequest, sharedPlayerState)
    val currentQueue = queue
    if (currentQueue.isNotEmpty()) {
      var startingIndex = 0
      var startingPosition = Millis(0)
      if (positionState.queueIndex in currentQueue.indices) {
        startingIndex = positionState.queueIndex
        startingPosition = positionState.playbackPosition
      }
      var item = try {
        currentQueue[startingIndex]
      } catch (e: Throwable) {
        NullPlayableAudioItem
      }
      if (!item.isValid || item.id != positionState.mediaId) {
        startingIndex = 0
        item = currentQueue[startingIndex]
        updatePositionState(QueuePositionState(item.id, startingIndex, Millis(0)))
      }
      queueState.update {
        it.copy(
          queue = currentQueue,
          queueIndex = startingIndex,
          position = startingPosition
        )
      }
      setSessionQueue(currentQueue)
      if (!resume) {
        transitionToNext(
          NullPlayableAudioItem,
          item,
          if (playNow()) DirectTransition() else NoOpMediaTransition,
          playNow,
          startingPosition,
          positionState.timePlayed,
          positionState.countingFrom
        )
      }
    } else {
      isActive.value = true
    }
  }

  /**
   * Should call this function any time the [queue] or currentItemIndex changes. [indexChange]
   * should only be true if this is called only do the the current item index changing.
   */
  private fun setSessionQueue(queue: List<PlayableAudioItem>, indexChange: Boolean = false) {
    sessionState.setQueue(queue, currentItemIndex, indexChange)
  }

  private fun reactToEqModeChanges() {
    sharedPlayerState.eqMode
      .onEach { mode -> queueState.update { it.copy(eqMode = mode) } }
      .catch { cause -> LOG.e(cause) { it("Error handling EqMode change") } }
      .launchIn(scope)
  }

  private fun reactToHeadsetChanges() {
    audioOutputState.stateChange
      .onEach { event -> handleOutputStateEvent(event) }
      .catch { cause -> LOG.e(cause) { it("Error in audio output state change flow") } }
      .launchIn(scope)
  }

  private fun reactToAudioFocusChanges() {
    LOG._e { it("reactToAudioFocusChanges") }
    audioFocusManager.reactionFlow
      .onStart { LOG._e { it("Audio focus reaction flow started") } }
      .onEach { reaction -> handleFocusReaction(reaction) }
      .catch { cause -> LOG.e(cause) { it("Error in audio focus reaction flow") } }
      .onCompletion { LOG._e { it("Audio focus reaction flow completed") } }
      .launchIn(scope)
  }

  fun StorePref<*, *>.keyValue(): Pair<Preferences.Key<*>, Any?> = key to invoke()

  /**
   * List of LibVlc prefs that will cause a [LibVlcSingleton.reset] and/or a reset of the
   * current items media player. If [LibVlcPrefs.audioOutputModule] changes, there's no reason
   * to reset LibVlcSingleton as the setting is read whenever a player is created (reset).
   */
  private val reactToPrefs = listOf(
    libVlcPrefs.enableVerboseMode,
    libVlcPrefs.audioOutputModule,
    libVlcPrefs.chroma,
    libVlcPrefs.networkCachingAmount,
    libVlcPrefs.subtitleEncoding,
    libVlcPrefs.replayGainMode,
    libVlcPrefs.replayPreamp,
    libVlcPrefs.defaultReplayGain,
    libVlcPrefs.enableFrameSkip,
    libVlcPrefs.skipLoopFilter,
    libVlcPrefs.allowTimeStretchAudio,
    libVlcPrefs.hardwareAcceleration,
  )

  private fun reactToLibVlcPrefs() {
    val libVlcPrefsMap = reactToPrefs.associateTo(mutableMapOf()) { it.keyValue() }
    val audioOutputModuleKey = libVlcPrefs.audioOutputModule.key
    libVlcPrefs.updateFlow
      .map { holder -> holder.store }
      .onEach {
        reactToPrefs.asSequence()
          .map { preference -> preference.keyValue() }
          .map { (key, value) -> if (libVlcPrefsMap.put(key, value) != value) key else null }
          .filterNotNull()
          .filterNot { key -> (key == audioOutputModuleKey).also { if (it) resetCurrent() } }
          .any { resetLibVlcSingleton() }
      }
      .launchIn(scope)
  }

  private fun resetLibVlcSingleton(): Boolean {
    LOG._e { it("resetLibVlcSingleton") }
    libVlcSingleton.reset()
    resetCurrent()
    return true
  }

  private fun resetCurrent() {
    LOG._e { it("resetCurrent") }
    currentItem.reset(PlayNow(playState.isPlaying))
  }

  private fun reactToRepeatModeChanges() {
    prefs.repeatMode
      .asFlow()
      .onEach { mode ->
        queueState.update { it.copy(repeatMode = mode) }
        sessionState.setRepeat(mode)
      }
      .launchIn(scope)
  }

  private fun reactToShuffleChanges() {
    prefs.shuffleMode
      .asFlow()
      .onEach { mode ->
        val newIsShuffled = mode.shuffleMedia
        if (lastShuffleMode.shuffleMedia != newIsShuffled) toggleShuffleMedia(newIsShuffled)
        lastShuffleMode = mode
        queueState.update { it.copy(shuffleMode = mode) }
        sessionState.setShuffle(mode)
      }
      .launchIn(scope)
  }

  override fun deactivate() {
    scope.cancel()
    positionState = QueuePositionState.INACTIVE_QUEUE_STATE
  }

  private fun getItemFromIndex(index: Int): PlayableAudioItem {
    val activeQueue = queue
    return if (index in activeQueue.indices) activeQueue[index] else NullPlayableAudioItem
  }

  private suspend fun getNextMediaTitle(): Title = if (isActive.value) {
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

  override fun play(immediate: Boolean) = currentItem.play(immediate)

  private fun playIfTelephoneIdle(delay: Millis) {
    if (telephonyManager.isIdle) scope.launch {
      delay(delay.value)
      if (telephonyManager.isIdle) play()
    }
  }

  override fun pause(immediateTransition: Boolean) = currentItem.pause(immediateTransition)
  override fun stop() = currentItem.stop()
  override fun togglePlayPause() = currentItem.togglePlayPause()

  /**
   * Using [playState] to determine if next song should play as it better represents current
   * "desired" state better than what an item/player is currently doing. eg. when rapidly switching
   * between songs we want to maintain the state of "playing", even though the various items/players
   * will be moving through various states.
   */
  override fun next() {
    scope.launch { nextSong(PlayNow(playState.isPlaying), manualTransition) }
  }

  /**
   * Using [playState] to determine if next song should play as it better represents current
   * "desired" state better than what an item/player is currently doing. eg. when rapidly switching
   * between songs we want to maintain the state of "playing", even though the various items/players
   * will be moving through various states.
   */
  override fun previous() {
    if (queueContainsMedia()) {
      val current = currentItem
      if (current.previousShouldRewind()) {
        current.seekTo(Millis(0))
      } else {
        val newIndex = positionState.queueIndex - 1
        if (newIndex < 0) {
          // start of queue
        } else {
          scope.launch { doGoToIndex(newIndex, PlayNow(playState.isPlaying), manualTransition) }
        }
      }
    }
  }

  override fun nextList() {
    scope.launch {
      val nextList = getNextList(prefs.lastListType(), prefs.lastListName())
      if (nextList.isNotEmpty) {
        playNext(
          if (shuffle.shuffleMedia) nextList.shuffled() else nextList,
          ClearQueue(true),
          PlayNow(playState.isPlaying)
        )
      }
    }
  }

  override fun previousList() {
    scope.launch {
      val prevList = getPreviousList(prefs.lastListType(), prefs.lastListName())
      LOG._e { it("nextType=%s nextName=%s", prevList.listType, prevList.listName) }
      if (prevList.isNotEmpty) {
        playNext(
          if (shuffle.shuffleMedia) prevList.shuffled() else prevList,
          ClearQueue(true),
          PlayNow(playState.isPlaying)
        )
      }
    }
  }

  override fun seekTo(position: Millis) = currentItem.seekTo(position)

  override fun fastForward() {
    val current = currentItem
    val position = current.position
    val end = current.duration - Millis.FIVE_SECONDS
    if (position < end) current.seekTo((position + Millis.TEN_SECONDS).coerceAtMost(end))
  }

  override fun rewind() {
    val current = currentItem
    current.seekTo((current.position - Millis.TEN_SECONDS).coerceAtLeast(Millis(0)))
  }

  override fun goToIndexMaybePlay(index: Int) {
    if (index != currentItemIndex) {
      scope.launch { doGoToIndex(index, PlayNow(currentItem.isPlaying), manualTransition) }
    }
  }

  override suspend fun addToUpNext(audioIdList: AudioIdList) {
    if (upNextQueue.isEmpty()) {
      playNext(audioIdList, ClearQueue(true), PlayNow(false), transition = manualTransition)
    } else {
      retryQueueChange {
        val prevCurrentIndex = currentItemIndex
        val prevCurrent = currentItem
        val idList = makeIdListLessCurrent(audioIdList, prevCurrent)

        if (idList.isNotEmpty()) {
          val (newQueue, newShuffled, newIndex) = addItemsToQueues(
            currentQueue = upNextQueue,
            currentShuffled = upNextShuffled,
            prevCurrent = prevCurrent,
            prevCurrentIndex = prevCurrentIndex,
            idList = idList,
            shuffleMode = shuffle,
            addAt = AddAt.AtEnd,
            clearAndRemakeQueues = false
          )
          persistIfCurrentUnchanged(
            newQueue = newQueue,
            newShuffled = newShuffled,
            prevCurrent = prevCurrent,
            prevCurrentIndex = prevCurrentIndex
          )
          establishNewQueues(newQueue, newShuffled, newIndex)
        }
      }
    }
  }

  override suspend fun prepareNext(audioIdList: AudioIdList) =
    playNext(audioIdList, ClearQueue(false), PlayNow(false), manualTransition)

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
    retryQueueChange {
      val prevCurrentIndex = currentItemIndex
      val prevCurrent = currentItem
      val firstIsCurrent = audioIdList.isNotEmpty && audioIdList.idList[0] == prevCurrent.id
      val idList = makeIdListLessCurrent(audioIdList, prevCurrent)

      if (idList.isNotEmpty()) {
        val upNextEmptyOrCleared = upNextQueue.isEmpty() || clearUpNext() || !prevCurrent.isValid
        val prevLast = getLastQueueItem()

        val (newQueue, newShuffled, newIndex) = addItemsToQueues(
          currentQueue = upNextQueue,
          currentShuffled = upNextShuffled,
          prevCurrent = prevCurrent,
          prevCurrentIndex = prevCurrentIndex,
          idList = idList,
          shuffleMode = shuffle,
          addAt = AddAt.AfterCurrent,
          clearAndRemakeQueues = upNextEmptyOrCleared
        )

        persistIfCurrentUnchanged(
          newQueue = newQueue,
          newShuffled = newShuffled,
          prevCurrent = prevCurrent,
          prevCurrentIndex = prevCurrentIndex,
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
          transitionToNext(prevCurrent, currentItem, transition, playNow, Millis(0))
        } else if (playNow()) {
          nextSong(PlayNow(true), transition)
        }
      } else if (firstIsCurrent) {
        transitionToNext(prevCurrent, currentItem, transition, playNow, Millis(0))
      }
    }
  }

  private suspend fun toggleShuffleMedia(shouldShuffle: Boolean) {
    retryQueueChange {
      if (upNextQueue.isNotEmpty()) {
        val prevCurrentIndex = currentItemIndex
        val prevCurrent = currentItem

        val queueIndex: Int
        val newShuffled: QueueList

        if (shouldShuffle) {
          newShuffled = ArrayList(upNextQueue)
          val queueMediaItem = upNextQueue[prevCurrentIndex]
          newShuffled.removeWithInstanceId(queueMediaItem)
          newShuffled.shuffle()
          newShuffled.add(0, queueMediaItem)
          queueIndex = 0
        } else {
          val queueMediaItem = upNextShuffled[prevCurrentIndex]
          queueIndex = upNextQueue.indexOfFirst { it.instanceId == queueMediaItem.instanceId }
          newShuffled = mutableListOf()
        }

        persistIfCurrentUnchanged(
          newQueue = upNextQueue,
          newShuffled = newShuffled,
          prevCurrent = prevCurrent,
          prevCurrentIndex = prevCurrentIndex,
          skipUnchangedCheck = true
        )
        establishNewQueues(upNextQueue, newShuffled, queueIndex)
      }
    }
  }

  override fun goToQueueItem(instanceId: Long) {
    val activeQueue = queue
    val index = activeQueue.indexOfFirst { it.instanceId == instanceId }
    if (index in activeQueue.indices) goToIndexMaybePlay(index)
  }

  private fun queueContainsMedia(): Boolean = queue.isNotEmpty()
  private fun atEndOfQueue(): Boolean = currentItemIndex >= queue.size - 1

  private suspend fun nextSong(
    playNow: PlayNow,
    transitionPair: PlayerTransitionPair
  ): Boolean {
    var success = false
    if (queueContainsMedia()) {
      if (!atEndOfQueue()) {
        doGoToIndex(currentItemIndex + 1, playNow, transitionPair)
        success = true
      } else {
        if (repeat.queue) {
          doGoToIndex(0, playNow, transitionPair)
          success = true
        } else {
          val endOfQueueAction = appPrefs.endOfQueueAction()
          if (endOfQueueAction.shouldGoToNextList) {
            // determine next list and play it, clearing the queue
            val currentListType = prefs.lastListType()
            val currentListName = prefs.lastListName()
            val nextList = getNextList(currentListType, currentListName)
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
    return success
  }

  private suspend fun getNextList(
    currentListType: SongListType,
    currentListName: String
  ): AudioIdList {
    val countResult = audioMediaDao.getCountAllAudio()
    return if (countResult is Ok && countResult.value > 0) {
      var nextList = if (shuffle.shuffleLists) {
        SongListType.getRandomType().getRandomList(audioMediaDao)
      } else {
        currentListType.getNextList(audioMediaDao, currentListName)
      }
      while (nextList.isEmpty) {
        nextList = nextList.listType.getNextList(audioMediaDao, nextList.listName)
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

  private suspend fun getPreviousList(
    currentListType: SongListType,
    currentListName: String
  ): AudioIdList {
    val countResult = audioMediaDao.getCountAllAudio()
    return if (countResult is Ok && countResult.value > 0) {
      var nextList = if (shuffle.shuffleLists) {
        SongListType.getRandomType().getRandomList(audioMediaDao)
      } else {
        currentListType.getPreviousList(audioMediaDao, currentListName)
      }
      while (nextList.isEmpty) {
        nextList = nextList.listType.getPreviousList(audioMediaDao, nextList.listName)
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

  private fun doGoToIndex(
    goToIndex: Int,
    ensureSongPlays: PlayNow,
    transition: PlayerTransitionPair
  ) {
    debug { ensureUiThread() }
    val currentIndex = currentItemIndex
    val current = currentItem

    var nextItem: PlayableAudioItem = NullPlayableAudioItem
    var checkIfSkipped = false
    val activeQueue = queue
    if (activeQueue.isEmpty() || goToIndex < 0 || goToIndex >= activeQueue.size) {
      LOG.e {
        it("go to index out of bounds index=%d queueSize=%d", goToIndex, activeQueue.size)
      }
      return
    }

    if (current.isValid && goToIndex > currentIndex) {
      checkIfSkipped = true
    }

    if (goToIndex in activeQueue.indices) {
      if (goToIndex == currentIndex && current.isValid) {
        nextItem = current.cloneItem()
        replaceCurrentItemInQueue(currentIndex, current, nextItem)
      } else {
        nextItem = activeQueue[goToIndex]
        if (nextItem.isValid) {
          updatePositionState(QueuePositionState(nextItem.id, goToIndex, Millis(0)))
          setSessionQueue(activeQueue, indexChange = true)
        }
      }
    }
    if (nextItem.isValid) {
      if (checkIfSkipped) current.checkMarkSkipped()
      transitionToNext(current, nextItem, transition, ensureSongPlays, Millis(0))
    } else {
      updateMetadata()
    }
  }

  private fun replaceCurrentItemInQueue(
    index: Int,
    item: PlayableAudioItem,
    replacement: PlayableAudioItem
  ) {
    if (shuffle.shuffleMedia) {
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

  private fun transitionToNext(
    currentItem: PlayableAudioItem,
    nextItem: PlayableAudioItem,
    transition: PlayerTransitionPair,
    playNow: PlayNow,
    position: Millis,
    timePlayed: Millis = Millis(0),
    countFrom: Millis = Millis(0)
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
        timePlayed,
        countFrom
      )
    }
  }

  private fun PlayableAudioItem.collectEvents(doOnStart: suspend () -> Unit) = eventFlow
    .onStart { doOnStart() }
    .onEach { event -> handleAudioItemEvent(event) }
    .catch { cause -> LOG.e(cause) { it("%s%s event handled exception", id, title) } }
    .onCompletion { cause -> LOG._i(cause) { it("%s%s event flow completed", id, title) } }
    .launchIn(scope)

  private suspend fun handleAudioItemEvent(event: PlayableItemEvent) {
    when (event) {
      is PlayableItemEvent.Prepared -> onPrepared(event)
      is PlayableItemEvent.PositionUpdate -> onPositionUpdate(event)
      is PlayableItemEvent.Start -> onStart(event)
      is PlayableItemEvent.Paused -> onPaused(event)
      is PlayableItemEvent.Stopped -> onStopped(event)
      is PlayableItemEvent.PlaybackComplete -> onPlaybackCompleted(event)
      is PlayableItemEvent.Error -> onError(event)
      is PlayableItemEvent.None -> {
      }
    }
  }

  private suspend fun onPrepared(event: PlayableItemEvent.Prepared) {
    LOG._e { it("onPrepared") }
    isActive.value = true
    val item = event.audioItem
    event.duration
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = event.position))
    updateMetadata()
    updatePlaybackState(
      item,
      currentItemIndex,
      queue,
      if (item.isPlaying) PlayState.Playing else PlayState.Paused
    )

    scope.launch {
      val nextTitle = getNextMediaTitle()
//      getNotifier().postNextMediaTitleChanged(NextMediaTitleChangedEvent(nextTitle)) TODO
    }
  }

  private suspend fun onPositionUpdate(event: PlayableItemEvent.PositionUpdate) {
    val item = event.audioItem
    val current = currentItem
    if (item.isPlaying) {
      val shouldAutoAdvance = event.position.shouldAutoAdvanceFrom(event.duration)
      if (item.supportsFade && shouldAutoAdvance) {
        doNextOrRepeat()
      } else {
        updatePlaybackState(current, currentItemIndex, queue, PlayState.Playing)
      }
    } else {
      updatePlaybackState(current, currentItemIndex, queue, PlayState.Paused)
    }
  }

  private suspend fun onStart(event: PlayableItemEvent.Start) {
    playState = PlayState.Playing
    val item = event.audioItem
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = event.position))
    if (event.firstStart) {
      scrobbler.start(item)
    } else {
      scrobbler.resume(item)
    }
    updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Playing)
  }

  private suspend fun onPaused(event: PlayableItemEvent.Paused) {
    playState = PlayState.Paused
    val item = event.audioItem
    val position = event.position
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = position))
    scrobbler.pause(item)
    updatePlaybackState(currentItem, currentItemIndex, queue, PlayState.Paused)
  }

  private suspend fun onStopped(event: PlayableItemEvent.Stopped) {
    val item = event.audioItem
    val current = currentItem
    if (current.instanceId == item.instanceId) {
      playState = PlayState.Stopped
      // TODO unsure our reported position is valid anymore, let's stick with last position for now
//    updateQueueState(queueState.copy(mediaId = item.id, playbackPosition = event.currentPosition))
      updatePlaybackState(current, currentItemIndex, queue, PlayState.Stopped)
    }
  }

  private suspend fun onPlaybackCompleted(event: PlayableItemEvent.PlaybackComplete) {
    val item = event.audioItem
    val current = currentItem
    LOG._e { it("onPlaybackCompleted current=%d item=%d") }
    if (current.instanceId == item.instanceId) {
      val doNextOrRepeat = doNextOrRepeat()
      LOG._e { it("doNextOrRepeat=%s", doNextOrRepeat) }
      if (!doNextOrRepeat) {
        playState = PlayState.Paused
        updatePlaybackState(current, currentItemIndex, queue, PlayState.Paused)
      }
    }
  }

  private suspend fun onError(event: PlayableItemEvent.Error) {
    val item = event.audioItem
    LOG.e { it("Playback error for item %s, %s", item.title.value, item.albumArtist.value) }
    nextSong(PlayNow(playState.isPlaying), manualTransition)
//    getNotifier().onError(item) TODO
  }

  private suspend fun updatePlaybackState(
    item: PlayableAudioItem,
    index: Int,
    queue: List<PlayableAudioItem>,
    playState: PlayState
  ) {
    queueState.update {
      it.copy(
        queue = queue,
        queueIndex = index,
        position = item.position,
        duration = item.duration,
        playingState = playState
      )
    }
    sessionState.setState(
      PlaybackState(
        playState,
        item.position,
        PlaybackRate.NORMAL,
        item.instanceId,
        PlaybackActions(
          hasMedia = queue.isNotEmpty(),
          isPlaying = item.isPlaying,
          hasPrev = index > 0 || item.position > Millis.FIVE_SECONDS
        )
      )
    )
  }

  private fun updateMetadata() = sessionState.setMetadata(currentItem.metadata)

  private fun updatePositionState(newState: QueuePositionState) {
    positionState = queuePositionStateDao.persistState(newState)
  }

  private fun handleOutputStateEvent(event: AudioOutputState.Event) {
    when (event) {
      AudioOutputState.Event.BluetoothConnected -> {
        if (appPrefs.playOnBluetoothConnection()) playIfTelephoneIdle(Millis.TWO_SECONDS)
      }
      AudioOutputState.Event.BluetoothDisconnected -> Unit
      AudioOutputState.Event.HeadsetConnected -> {
        if (appPrefs.playOnWiredConnection()) playIfTelephoneIdle(Millis.ONE_SECOND)
      }
      AudioOutputState.Event.HeadsetDisconnected -> Unit
      AudioOutputState.Event.None -> Unit
    }
  }

  private fun handleFocusReaction(reaction: FocusReaction) {
    when (reaction) {
      FocusReaction.Play -> play()
      FocusReaction.Pause -> pause()
      FocusReaction.StopForeground -> stop()
      FocusReaction.Duck -> duck()
      FocusReaction.EndDuck -> endDuck()
      FocusReaction.None -> Unit
    }
  }

  private fun duck() {
    val state = appPrefs.duckAction()
    sharedPlayerState.duckedState = state
    when (state) {
      DuckAction.Duck -> currentItem.duck()
      DuckAction.Pause -> currentItem.pause(immediate = true)
      DuckAction.None -> Unit
    }
  }

  private fun endDuck() {
    val state = sharedPlayerState.duckedState
    sharedPlayerState.duckedState = DuckAction.None
    when (state) {
      DuckAction.Duck -> currentItem.endDuck()
      DuckAction.Pause -> currentItem.play(immediate = true)
      DuckAction.None -> Unit
    }
  }

  private suspend fun doNextOrRepeat(): Boolean = if (repeat.current) {
    doGoToIndex(currentItemIndex, PlayNow(true), autoAdvanceTransition)
    true
  } else {
    nextSong(PlayNow(true), autoAdvanceTransition)
  }

  private fun Millis.shouldAutoAdvanceFrom(duration: Millis): Boolean {
    //if (!atEndOfQueue() && appPrefs.autoAdvanceFade()) {
    if (appPrefs.autoAdvanceFade()) {
      val fadeLength = appPrefs.autoAdvanceFadeLength()
      if (duration > fadeLength && duration - this < fadeLength + OFFSET_CONSIDERED_END) {
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
    // Never change the underlying queues except on the UI thread
    debug { ensureUiThread() }
    var index = newIndex
    var item: PlayableAudioItem = NullPlayableAudioItem
    upNextQueue = newQueue
    upNextShuffled = newShuffled
    val activeQueue = queue
    if (activeQueue.isNotEmpty()) {
      if (clearUpNext) {
        index = 0
        item = activeQueue[index]
        updatePositionState(QueuePositionState(item.id, index, item.position))
      } else {
        item = activeQueue[index]
        updatePositionState(QueuePositionState(item.id, index, item.position))
      }
    } else {
      index = -1
      queuePositionStateDao.persistState(QueuePositionState.INACTIVE_QUEUE_STATE)
    }
    queueState.update {
      it.copy(
        queue = activeQueue,
        queueIndex = index,
        position = item.position,
        duration = item.duration,
        playingState = playState,
      )
    }
    setSessionQueue(activeQueue)
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
    val allowDuplicates = AllowDuplicates(appPrefs.allowDuplicates())
    val newQueueItems =
      itemFactory.makeNewQueueItems(idList, focusRequest, sharedPlayerState)

    if (clearAndRemakeQueues) {
      // if the queue is empty OR we are supposed to clear the queue, just build new list(s)
      newQueue = newQueueItems
      newShuffled = maybeMakeShuffled(shuffleMode, newQueue)
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
          shuffle
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
    // TODO this can silently fail and the queues are updated to items not persisted
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
}

class QueueChangedException(message: String) : Exception(message)
/**
 * From Roman Elizarov https://stackoverflow.com/a/46890009/2660904
 */
private suspend fun <T> retryQueueChange(
  times: Int = MAX_QUEUE_RETRIES,
  initialDelay: Millis = Millis(100),
  maxDelay: Millis = Millis(800),
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

private fun List<PlayableAudioItem>.itemIsAtIndex(item: PlayableAudioItem, index: Int): Boolean =
  if (index in indices) this[index].instanceId == item.instanceId else false

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

val TelephonyManager.isIdle: Boolean get() = callState == TelephonyManager.CALL_STATE_IDLE
