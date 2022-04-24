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

package com.ealva.toque.service.audio

import android.media.AudioManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.datastore.preferences.core.Preferences
import com.ealva.ealvabrainz.common.AlbumTitle
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.prefstore.store.MutablePreferenceStore
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.R
import com.ealva.toque.android.telcom.isIdle
import com.ealva.toque.app.Toque
import com.ealva.toque.audio.AudioItem
import com.ealva.toque.audioout.AudioOutputState
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.QueueChangedException
import com.ealva.toque.common.Rating
import com.ealva.toque.common.RepeatMode
import com.ealva.toque.common.ShuffleLists
import com.ealva.toque.common.ShuffleMedia
import com.ealva.toque.common.ShuffleMode
import com.ealva.toque.common.StarRating
import com.ealva.toque.common.Title
import com.ealva.toque.common.alsoIf
import com.ealva.toque.common.asMillis
import com.ealva.toque.common.debug
import com.ealva.toque.common.debugCheck
import com.ealva.toque.common.debugRequire
import com.ealva.toque.common.ensureUiThread
import com.ealva.toque.common.fetch
import com.ealva.toque.common.moveItem
import com.ealva.toque.common.toRating
import com.ealva.toque.db.AlbumDao
import com.ealva.toque.db.AlbumDaoEvent
import com.ealva.toque.db.AudioMediaDao
import com.ealva.toque.db.CategoryMediaList
import com.ealva.toque.db.CategoryToken
import com.ealva.toque.db.QueueDao
import com.ealva.toque.db.QueuePositionState
import com.ealva.toque.db.QueuePositionStateDao
import com.ealva.toque.db.QueuePositionStateDaoFactory
import com.ealva.toque.log._i
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.InstanceId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.service.audio.TransitionType.Manual
import com.ealva.toque.service.media.EqMode
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.PlayState
import com.ealva.toque.service.notify.ServiceNotification
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayer.Companion.OFFSET_CONSIDERED_END
import com.ealva.toque.service.player.CrossFadeTransition
import com.ealva.toque.service.player.DirectTransition
import com.ealva.toque.service.player.NoOpMediaTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PlayerTransitionPair
import com.ealva.toque.service.queue.ClearQueue
import com.ealva.toque.service.queue.MayFade
import com.ealva.toque.service.queue.MayFade.AllowFade
import com.ealva.toque.service.queue.MayFade.NoFade
import com.ealva.toque.service.queue.MusicStreamVolume
import com.ealva.toque.service.queue.PlayNow
import com.ealva.toque.service.queue.PlayableMediaQueue
import com.ealva.toque.service.queue.QueuePrefs
import com.ealva.toque.service.queue.QueuePrefsSingleton
import com.ealva.toque.service.queue.QueueType
import com.ealva.toque.service.queue.ScreenAction
import com.ealva.toque.service.queue.StreamVolume
import com.ealva.toque.service.session.common.PlaybackActions
import com.ealva.toque.service.session.common.PlaybackState
import com.ealva.toque.service.session.server.AudioFocusManager
import com.ealva.toque.service.session.server.AudioFocusManager.FocusReaction
import com.ealva.toque.service.session.server.MediaSessionControl
import com.ealva.toque.service.session.server.MediaSessionState
import com.ealva.toque.service.vlc.LibVlcPrefs
import com.ealva.toque.service.vlc.LibVlcPrefsSingleton
import com.ealva.toque.service.vlc.LibVlcSingleton
import com.ealva.toque.ui.lock.LockScreenActivity
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import it.unimi.dsi.fastutil.longs.LongCollection
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.time.Duration

typealias QueueList = List<PlayableAudioItem>

data class LocalAudioQueueState(
  val queue: List<AudioItem>,
  val queueIndex: Int,
  val position: Millis,
  val duration: Duration,
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
      position = Millis.ZERO,
      duration = Duration.ZERO,
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
  override val queueType: QueueType get() = QueueType.Audio

  /** Represents the current "state" of the queue */
  val queueState: StateFlow<LocalAudioQueueState>

  /** Notification source for events the service to possibly by shown to the user */
  val notificationFlow: Flow<ServiceNotification>

  /** Toggles Eq off/on which is then reflected in new state emission */
  fun toggleEqMode()

  /** Set the rating of the current AudioItem */
  fun setRating(rating: StarRating, allowFileUpdate: Boolean = false)

  /** Add all media in [categoryMediaList] to the Up Next queue and return the new queue size*/
  suspend fun addToUpNext(categoryMediaList: CategoryMediaList): Result<QueueSize, Throwable>

  /**
   * Add all the media in [categoryMediaList] ot the Up Next queue and maybe play. If [clearUpNext]
   * is true the queue is cleared before items are added, else the media are added after the current
   * item. If [playNow] is true the first item in the list will be played, moving to the next song
   * if necessary. The [transitionType] determines if/how currently playing media fades into the
   * next media.
   */
  suspend fun playNext(
    categoryMediaList: CategoryMediaList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transitionType: TransitionType
  ): Result<QueueSize, Throwable>

  /** Go to the queue item represented by the unique [instanceId] */
  fun goToQueueItem(instanceId: InstanceId)

  /** Same as [playNext] with [ClearQueue] false and [PlayNow] false */
  suspend fun prepareNext(categoryMediaList: CategoryMediaList)

  /** This method is necessary for MediaSession callback */
  fun setRepeatMode(mode: RepeatMode)

  /** This method is necessary for MediaSession callback */
  fun setShuffleMode(mode: ShuffleMode)

  /** Remove the [item], which should be at [index], from the Up Next Queue */
  suspend fun removeFromQueue(index: Int, item: AudioItem)

  /**
   * Move the queue it at the [from] index to the [to] index position. Other items and current
   * index are updated as appropriate
   */
  fun moveQueueItem(from: Int, to: Int)

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
      val dao = queuePositionStateDaoFactory.makeStateDao(AudioMediaDao.QUEUE_ID)
      val positionState = dao.getState()
        .onFailure { cause -> LOG.e(cause) { it("Can't read queue state") } }
        .getOrElse { QueuePositionState.INACTIVE_QUEUE_STATE }

      return LocalAudioCommandProcessor(
        LocalAudioQueueImpl(
          queuePrefsSingleton.instance(),
          sessionState,
          positionState,
          dao,
          playableAudioItemFactory,
          MusicStreamVolume(audioManager),
          appPrefs,
          libVlcPrefsSingleton.instance()
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
) : LocalAudioQueue, TransitionSelector, KoinComponent {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val audioMediaDao: AudioMediaDao by inject()
  private val albumDao: AlbumDao by inject()
  private val queueDao: QueueDao by inject()
  private val eqPresetFactory: EqPresetFactory by inject()
  private val audioOutputState: AudioOutputState by inject()
  private var sharedPlayerState: SharedPlayerState = NullSharedPlayerState
  private val audioFocusManager: AudioFocusManager by inject()
  private val telecomManager: TelecomManager by inject()
  private val libVlcSingleton: LibVlcSingleton by inject()
  private var currentItemFlowJob: Job? = null
  private var playState: PlayState = PlayState.None
  private var positionState: QueuePositionState = initialState
  private var lastShuffle = prefs.shuffleMode()

  override val queueState = MutableStateFlow(makeInitialState())
  override val notificationFlow = MutableSharedFlow<ServiceNotification>()

  private val focusRequest = AvPlayer.FocusRequest {
    audioFocusManager.requestFocus(AudioFocusManager.ContentType.Audio) { playState.isPlaying }
  }

  private fun makeInitialState(): LocalAudioQueueState = LocalAudioQueueState(
    emptyList(),
    -1,
    Millis.ZERO,
    Duration.ZERO,
    PlayState.None,
    prefs.repeatMode(),
    prefs.shuffleMode(),
    sharedPlayerState.eqMode.value,
    EqPreset.NONE,
    ""
  )

  private inline fun updateQueueState(
    @Suppress("UNUSED_PARAMETER") why: String,
    block: (LocalAudioQueueState) -> LocalAudioQueueState
  ) = queueState.update { state -> block(state) }

  private val repeat: RepeatMode get() = prefs.repeatMode()

  override fun nextRepeatMode() = setRepeatMode(repeat.getNext())

  private fun <T : PreferenceStore<T>> T.asyncEdit(block: T.(MutablePreferenceStore) -> Unit) {
    scope.launch { edit(block) }
  }

  override fun setRepeatMode(mode: RepeatMode) = prefs.asyncEdit { it[repeatMode] = mode }

  private inline val shuffleMode: ShuffleMode get() = prefs.shuffleMode()
  private inline val shuffleLists: ShuffleLists get() = shuffleMode.shuffleLists
  private inline val shuffleMedia: ShuffleMedia get() = shuffleMode.shuffleMedia

  override fun nextShuffleMode() = setShuffleMode(shuffleMode.getNext())

  override fun setShuffleMode(mode: ShuffleMode) = prefs.asyncEdit { it[shuffleMode] = mode }

  override fun setRating(rating: StarRating, allowFileUpdate: Boolean) {
    currentItem.setRating(rating.toRating(), allowFileUpdate)
    updateMetadata()
  }

  /**
   * Toggle EqMode but UI is updated listening to [SharedPlayerState.eqMode], which is already
   * a StateFlow (compared to [QueuePrefs.eqMode]
   */
  override fun toggleEqMode() = prefs.asyncEdit { it[eqMode] = prefs.eqMode().next() }

  private val queue: List<PlayableAudioItem>
    get() = if (shuffleMedia.value) upNextShuffled else upNextQueue

  private val currentItem: PlayableAudioItem get() = queue.getItemFromIndex(currentItemIndex)
  private val currentItemIndex: Int get() = positionState.queueIndex
  private val nextItem: PlayableAudioItem get() = queue.getItemFromIndex(currentItemIndex + 1)
  private var upNextQueue: QueueList = mutableListOf()
  private var upNextShuffled: QueueList = mutableListOf()

  override val manualTransition: PlayerTransitionPair
    get() = if (appPrefs.manualChangeFade()) {
      CrossFadeTransition(appPrefs.manualChangeFadeLength())
    } else {
      DirectTransition()
    }

  override val autoAdvanceTransition: PlayerTransitionPair
    get() = if (appPrefs.autoAdvanceFade()) {
      CrossFadeTransition(appPrefs.autoAdvanceFadeDuration())
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
    reactToAlbumUpdates()
    sessionState.contentType = AudioFocusManager.ContentType.Audio
    sessionState.setQueueTitle(fetch(R.string.LocalAudio))
    upNextQueue = itemFactory.makeUpNextQueue(false, focusRequest, sharedPlayerState)
    upNextShuffled = itemFactory.makeShuffledQueue(upNextQueue)
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
        LOG.e { it("Item not valid to activate. Item:%s LastPosition:%s", item, positionState) }
        startingIndex = 0
        item = currentQueue[startingIndex]
        updatePositionState(QueuePositionState(item.id, startingIndex, Millis(0)))
      }
      updateQueueState("activate") { state ->
        state.copy(
          queue = currentQueue.asLocalAudioItemList,
          queueIndex = startingIndex,
          duration = item.duration,
          position = startingPosition,
          playingState = if (currentQueue.isNotEmpty()) PlayState.Paused else PlayState.None
        )
      }
      setSessionQueue(currentQueue)
      if (!resume) {
        transitionToNext(
          NullPlayableAudioItem,
          item,
          if (playNow.value) DirectTransition() else NoOpMediaTransition,
          playNow,
          startingPosition
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

  private fun reactToAlbumUpdates() = albumDao.albumDaoEvents
    .onEach { event -> if (event is AlbumDaoEvent.AlbumArtworkUpdated) handleAlbumArtChange(event) }
    .launchIn(scope)

  private fun handleAlbumArtChange(event: AlbumDaoEvent.AlbumArtworkUpdated) = queue
    .filter { item -> item.albumId == event.albumId }
    .onEach { item -> item.updateArtwork(event.albumId, event.albumArt, event.localAlbumArt) }
    .takeIf { updatedList -> updatedList.isNotEmpty() }
    ?.updateQueueStateMaybeMetadata()

  private fun List<PlayableAudioItem>.updateQueueStateMaybeMetadata() {
    updateQueueState("updateQueueStateMaybeMetadata") { state ->
      state.copy(queue = queue.asLocalAudioItemList)
    }
    if (any { audioItem -> audioItem.instanceId == currentItem.instanceId }) {
      updateMetadata()
      updatePlaybackState(playState)
    }
  }

  private fun reactToEqModeChanges() {
    sharedPlayerState.eqMode
      .onEach { mode -> handleNewEqMode(mode) }
      .catch { cause -> LOG.e(cause) { it("EqMode flow error") } }
      .launchIn(scope)
  }

  private fun handleNewEqMode(mode: EqMode) {
    updateQueueState("handleNewEqMode") { state -> state.copy(eqMode = mode) }
    notify(ServiceNotification(fetch(mode.titleRes)))
  }

  private fun reactToHeadsetChanges() {
    audioOutputState.stateChange
      .onEach { event -> handleOutputStateEvent(event) }
      .catch { cause -> LOG.e(cause) { it("Audio output state change flow error") } }
      .launchIn(scope)
  }

  private fun reactToAudioFocusChanges() {
    audioFocusManager.reactionFlow
      .onEach { reaction -> handleFocusReaction(reaction) }
      .catch { cause -> LOG.e(cause) { it("Audio focus reaction flow error") } }
      .onCompletion { LOG._i { it("Audio focus reaction flow completed") } }
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
    libVlcPrefs.replayGainPreamp,
    libVlcPrefs.defaultReplayGain,
    libVlcPrefs.enableFrameSkip,
    libVlcPrefs.skipLoopFilter,
    libVlcPrefs.allowTimeStretchAudio,
    libVlcPrefs.hardwareAcceleration,
  )

  private fun reactToLibVlcPrefs() {
    val libVlcPrefsMap = reactToPrefs.associateTo(mutableMapOf()) { it.keyValue() }

    fun Preferences.Key<*>.isOutputModuleAlsoReset(): Boolean =
      this == libVlcPrefs.audioOutputModule.key.also { resetCurrent() }

    /*
     * If the AudioOutputModule changes, just reset the current player. On any other preference
     * change reset LibVLC and then reset the current player.
     */
    libVlcPrefs.updateFlow
      .map { holder -> holder.store }
      .onEach {
        if (reactToPrefs.asSequence()
            .map { preference -> preference.keyValue() }
            .map { (key, value) -> if (libVlcPrefsMap.put(key, value) != value) key else null }
            .filterNotNull()
            .filterNot { key -> key.isOutputModuleAlsoReset() }
            .any()
        ) resetLibVlcSingleton()
      }
      .catch { cause -> LOG.e(cause) { it("LibVLCPrefs flow error") } }
      .launchIn(scope)
  }

  private suspend fun resetLibVlcSingleton() {
    libVlcSingleton.reset()
    resetCurrent()
  }

  private fun resetCurrent() {
    currentItem.reset(PlayNow(playState.isPlaying))
  }

  private fun reactToRepeatModeChanges() {
    prefs.repeatMode
      .asFlow()
      .onEach { mode -> handleNewRepeatMode(mode) }
      .launchIn(scope)
  }

  private fun handleNewRepeatMode(mode: RepeatMode) {
    updateQueueState("handleNewRepeatMode") { state -> state.copy(repeatMode = mode) }
    sessionState.setRepeatMode(mode)
    notify(ServiceNotification(fetch(mode.titleRes)))
  }

  private fun reactToShuffleChanges() {
    prefs.shuffleMode
      .asFlow()
      .onEach { newMode -> handleNewShuffleMode(newMode) }
      .launchIn(scope)
  }

  private suspend fun handleNewShuffleMode(newMode: ShuffleMode) {
    if (lastShuffle.shuffleMediaDiffers(newMode)) toggleShuffleMedia(lastShuffle, newMode)
    lastShuffle = newMode
    updateQueueState("handleNewShuffleMode") { state -> state.copy(shuffleMode = newMode) }
    sessionState.setShuffleMode(newMode)
    notify(ServiceNotification(fetch(newMode.titleRes)))
  }

  override fun deactivate() {
    scope.cancel()
    positionState = QueuePositionState.INACTIVE_QUEUE_STATE
  }

  private fun List<PlayableAudioItem>.getItemFromIndex(index: Int): PlayableAudioItem {
    return if (index in indices) this[index] else NullPlayableAudioItem
  }

  private suspend fun getNextMediaTitle(): Title = if (isActive.value) {
    val item = nextItem
    when {
      nextItem.isValid -> item.title
      queue.isNotEmpty() -> getNextListFirstMediaTitle()
      else -> Title("")
    }
  } else Title("")

  private suspend fun getNextListFirstMediaTitle(): Title = try {
    val nextList = getNextCategory()
    if (nextList.isNotEmpty) {
      audioMediaDao.getMediaTitle(nextList.idList[0])
        .onFailure { cause -> LOG.e(cause) { it("Error getting media title") } }
        .getOrElse { Title("") }
    } else Title("")
  } catch (e: Exception) {
    LOG.e(e) { it("Error getting next list, first title") }
    Title("")
  }

  override fun play(mayFade: MayFade) = currentItem.play(mayFade)

  private fun playIfTelephoneIdle(delay: Millis) {
    if (telecomManager.isIdle(true)) scope.launch {
      delay(delay.value)
      if (telecomManager.isIdle(false)) play(AllowFade)
    }
  }

  override fun pause(mayFade: MayFade) = currentItem.pause(mayFade)
  override fun stop() = currentItem.stop()
  override fun togglePlayPause() = currentItem.togglePlayPause()

  /**
   * Using [playState] to determine if next song should play as it better represents current
   * "desired" state better than what an item/player is currently doing. eg. when rapidly switching
   * between songs we want to maintain the state of "playing", even though the various items/players
   * will be moving through various states.
   */
  override fun next() {
    scope.launch { nextSong(PlayNow(playState.isPlaying), Manual) }
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
          doGoToIndex(newIndex, PlayNow(playState.isPlaying), Manual)
        }
      }
    }
  }

  override fun nextList() {
    scope.launch {
      val nextList = getNextCategory()
      if (nextList.isNotEmpty) {
        playNext(
          nextList,
          ClearQueue(true),
          PlayNow(playState.isPlaying),
          Manual
        )
      }
    }
  }

  override fun previousList() {
    scope.launch {
      val prevList = getPreviousCategory(
        CategoryToken(prefs.lastCategoryType(), prefs.lastCategoryId())
      )
      if (prevList.isNotEmpty) {
        playNext(
          prevList.maybeShuffle(shuffleMode),
          ClearQueue(true),
          PlayNow(playState.isPlaying),
          Manual
        )
      }
    }
  }

  override fun seekTo(position: Millis) = currentItem.seekTo(position)

  override fun fastForward() {
    val current = currentItem
    val position = current.position
    val end = current.duration.asMillis - Millis.FIVE_SECONDS
    if (position < end) current.seekTo((position + Millis.TEN_SECONDS).coerceAtMost(end))
  }

  override fun rewind() {
    val current = currentItem
    current.seekTo((current.position - Millis.TEN_SECONDS).coerceAtLeast(Millis(0)))
  }

  override fun goToIndexMaybePlay(index: Int) {
    if (index != currentItemIndex && index in queue.indices) {
      doGoToIndex(index, PlayNow(currentItem.isPlaying), Manual)
    }
  }

  override suspend fun addToUpNext(
    categoryMediaList: CategoryMediaList
  ): Result<QueueSize, Throwable> {
    return if (upNextQueue.isEmpty()) {
      playNext(categoryMediaList, ClearQueue(true), PlayNow(false), Manual)
    } else {
      runSuspendCatching {
        retryQueueChange {
          val prevCurrentIndex = currentItemIndex
          val prevCurrent = currentItem

          val allowDuplicates = AllowDuplicates(appPrefs.allowDuplicates())

          val idList: LongCollection = categoryMediaList.list
            .removeDuplicatesIf(remove = !allowDuplicates())
            .removeItemIf(item = prevCurrent.id.value, remove = !allowDuplicates())

          if (idList.isNotEmpty()) {
            val (newQueue, newShuffled, newIndex) = addItemsToQueues(
              currentQueue = upNextQueue,
              currentShuffled = upNextShuffled,
              prevCurrent = prevCurrent,
              prevCurrentIndex = prevCurrentIndex,
              idList = idList,
              addAt = AddAt.AtEnd,
              clearAndRemakeQueues = false,
              allowDuplicates = allowDuplicates,
              shuffleMedia = shuffleMedia
            )

            persistIfCurrentUnchanged(
              newQueue = newQueue,
              newShuffled = newShuffled,
              prevCurrent = prevCurrent,
              prevCurrentIndex = prevCurrentIndex
            )
            establishNewQueues(newQueue, newShuffled, newIndex)
            QueueSize(newQueue.size)
          } else throw IllegalArgumentException("List was empty")
        }
      }
    }
  }

  override suspend fun prepareNext(categoryMediaList: CategoryMediaList) {
    playNext(categoryMediaList, ClearQueue(false), PlayNow(false), Manual)
  }

  override suspend fun playNext(
    categoryMediaList: CategoryMediaList,
    clearUpNext: ClearQueue,
    playNow: PlayNow,
    transitionType: TransitionType
  ): Result<QueueSize, Throwable> = runSuspendCatching {
    // We will only swap the up next queue on the UI thread, which means we need to save current
    // information and only swap if the new queue "current" matches the old. If doesn't match the
    // current item changed while we were creating the new queue, need to loop and try again.
    // Retry because the queue changed should be very rare (such as auto advance while playing)
    retryQueueChange {
      val prevCurrentIndex = currentItemIndex
      val prevCurrent = currentItem
      val firstIsCurrent =
        categoryMediaList.isNotEmpty && categoryMediaList.idList[0] == prevCurrent.id

      when {
        categoryMediaList.isNotEmpty -> {
          val upNextEmptyOrCleared = upNextQueue.isEmpty() || clearUpNext() || !prevCurrent.isValid
          val allowDuplicates = AllowDuplicates(appPrefs.allowDuplicates())

          val idList: LongCollection = categoryMediaList.list
            .removeDuplicatesIf(!allowDuplicates())
            .removeItemIf(prevCurrent.id.value, !allowDuplicates() && !upNextEmptyOrCleared)

          val (newQueue, newShuffled, newIndex) = addItemsToQueues(
            currentQueue = upNextQueue,
            currentShuffled = upNextShuffled,
            prevCurrent = prevCurrent,
            prevCurrentIndex = prevCurrentIndex,
            idList = idList,
            addAt = AddAt.AfterCurrent,
            clearAndRemakeQueues = upNextEmptyOrCleared,
            allowDuplicates = allowDuplicates,
            shuffleMedia = shuffleMedia
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

          categoryMediaList.setAsLastList()

          if (upNextEmptyOrCleared || firstIsCurrent) {
            transitionToNext(prevCurrent, currentItem, select(transitionType), playNow, Millis(0))
          } else if (playNow.value) {
            nextSong(PlayNow(true), transitionType)
          }
          QueueSize(newQueue.size)
        }
        firstIsCurrent -> {
          transitionToNext(prevCurrent, currentItem, select(transitionType), playNow, Millis(0))
          QueueSize(queue.size)
        }
        else -> throw IllegalArgumentException("List was empty")
      }
    }
  }

  private fun notify(notification: ServiceNotification) {
    scope.launch { notificationFlow.emit(notification) }
  }

  override suspend fun removeFromQueue(index: Int, item: AudioItem) {
    val updateTime = Millis.currentUtcEpochMillis()
    val prevCurrentIndex = currentItemIndex
    val prevCurrent = currentItem

    var newQueue: MutableList<PlayableAudioItem> = upNextQueue.toMutableList()
    var newShuffled: MutableList<PlayableAudioItem> = upNextShuffled.toMutableList()
    var newIndex = index

    // Index to remove can't be current item, so result cannot be < 0 if index < prevCurrentIndex
    fun adjustNewIndex(index: Int, prevCurrentIndex: Int): Int =
      if (index < prevCurrentIndex) prevCurrentIndex - 1 else prevCurrentIndex

    var removed: PlayableAudioItem = NullPlayableAudioItem

    var unshuffledIndex = -1
    if (index != prevCurrentIndex && index in upNextQueue.indices) {
      if (shuffleMedia.value) {
        if (index in newShuffled.indices) {
          removed = newShuffled.removeAt(index)
          if (removed.instanceId == item.instanceId) {
            unshuffledIndex = newQueue.indexOfFirst { element ->
              element.instanceId == removed.instanceId
            }
            if (unshuffledIndex >= 0) newQueue.removeAt(unshuffledIndex)
            newIndex = adjustNewIndex(index, prevCurrentIndex)
          } else {
            // index and item didn't match - code defect somewhere
            removed = NullPlayableAudioItem
            newShuffled = upNextShuffled.toMutableList()
          }
        }
      } else {
        removed = newQueue.removeAt(index)
        if (removed.instanceId == item.instanceId) {
          newIndex = adjustNewIndex(index, prevCurrentIndex)
        } else {
          // index and item didn't match - code defect somewhere
          removed = NullPlayableAudioItem
          newQueue = upNextQueue.toMutableList()
        }
      }
      try {
        persistIfCurrentUnchanged(
          newQueue = newQueue,
          newShuffled = newShuffled,
          prevCurrent = prevCurrent,
          prevCurrentIndex = prevCurrentIndex,
          updateTime = updateTime
        )
      } catch (e: Exception) {
        LOG.e(e) { it("Could not persist new queue") }
        // fatal error persisting, revert to original queues and invoke establish so update is
        // sent to clients
        newQueue = upNextQueue.toMutableList()
        newShuffled = upNextShuffled.toMutableList()
        newIndex = prevCurrentIndex
      }
    }

    // We want to send an state update even if nothing changed to ensure the UI is consistent
    establishNewQueues(newQueue, newShuffled, newIndex)

    if (removed !== NullPlayableAudioItem) {
      notify(
        ServiceNotification(
          fetch(R.string.TitleRemovedFromQueue, removed.title.value),
          ItemRemovedFromQueueUndo(
            scope,
            removed,
            index,
            unshuffledIndex,
            updateTime,
            shuffleMedia
          )
        )
      )
    } else {
      notify(ServiceNotification(fetch(R.string.RemoveFailed, item.title.value, index)))
    }
  }

  private suspend fun undoRemoveItemFromQueue(
    item: PlayableAudioItem,
    index: Int,
    unshuffledIndex: Int,
    updateTime: Millis,
    shuffle: ShuffleMedia
  ) = runSuspendCatching {
    val currentIndex = currentItemIndex
    if (shuffle == shuffleMedia) {
      val newQueue: MutableList<PlayableAudioItem> = upNextQueue.toMutableList()
      val newShuffled: MutableList<PlayableAudioItem> = upNextShuffled.toMutableList()

      if (shuffle.value) {
        newShuffled.add(index, item)
        newQueue.add(unshuffledIndex.coerceIn(newQueue.indices), item)
      } else {
        newQueue.add(index, item)
      }

      persistIfQueueNotUpdated(
        newQueue = newQueue,
        newShuffled = newShuffled,
        lastUpdated = updateTime
      )

      establishNewQueues(
        newQueue = newQueue,
        newShuffled = newShuffled,
        newIndex = if (index <= currentIndex) currentIndex + 1 else currentIndex
      )
    }
  }.onFailure { cause ->
    LOG.e(cause) { it("undoRemoveItemFromQueue error") }
    notify(ServiceNotification(fetch(R.string.CouldNotUndoItem, item.title.value)))
  }

  inner class ItemRemovedFromQueueUndo(
    private val scope: CoroutineScope,
    private val item: PlayableAudioItem,
    private val index: Int,
    private val unshuffledIndex: Int,
    private val updateTime: Millis,
    private val shuffleMedia: ShuffleMedia // currently this is redundant as updateTime covers
  ) : ServiceNotification.Action {
    override val label: String = fetch(R.string.Undo)

    override fun action() {
      scope.launch {
        undoRemoveItemFromQueue(item, index, unshuffledIndex, updateTime, shuffleMedia)
      }
    }

    override fun expired() {
      LOG.i { it("ItemRemovedFromQueueUndo Expired title:%s index:%s", item.title.value, index) }
    }
  }

  override fun moveQueueItem(from: Int, to: Int) {
    if (from == to) return
    scope.launch {
      val index = currentItemIndex
      val item = currentItem

      val indexRange = upNextQueue.indices
      var newQueue = upNextQueue.toMutableList()
      var newShuffled = upNextShuffled.toMutableList()
      var newIndex = index

      if (from in indexRange && to in indexRange) {
        if (shuffleMedia.value) {
          newShuffled.moveItem(from, to)
        } else {
          newQueue.moveItem(from, to)
        }

        newIndex = when {
          from == index -> to
          from < index -> if (to >= index) index - 1 else index
          to <= index -> if (from > index) index + 1 else index
          else -> index
        }

        try {
          persistIfCurrentUnchanged(
            newQueue = newQueue,
            newShuffled = newShuffled,
            prevCurrent = item,
            prevCurrentIndex = index
          )
        } catch (e: Exception) {
          LOG.e(e) { it("Could not persist new queue") }
          // fatal error persisting, revert to original queues and invoke establish so update is
          // sent to clients
          newQueue = upNextQueue.toMutableList()
          newShuffled = upNextShuffled.toMutableList()
          newIndex = index
        }
      }
      // We want to send an state update even if nothing changed to ensure the UI is consistent
      establishNewQueues(newQueue, newShuffled, newIndex)
    }
  }

  private suspend fun toggleShuffleMedia(prevMode: ShuffleMode, newMode: ShuffleMode) {
    debugRequire(prevMode.shuffleMediaDiffers(newMode)) {
      "Incorrectly called toggleShuffleMedia with old=$prevMode new=$newMode"
    }

    retryQueueChange {
      if (upNextQueue.isNotEmpty()) {
        val prevCurrentIndex = currentItemIndex
        val prevQueue = if (prevMode.shuffleMedia.value) upNextShuffled else upNextQueue
        val prevCurrentItem = prevQueue.getItemFromIndex(prevCurrentIndex)

        var queueIndex: Int
        val newShuffled: QueueList

        if (newMode.shuffleMedia.value) {
          newShuffled = ArrayList(prevQueue)
          newShuffled.removeWithInstanceId(prevCurrentItem.instanceId)
          newShuffled.shuffle()
          newShuffled.add(0, prevCurrentItem)
          queueIndex = 0
        } else {
          queueIndex = upNextQueue.indexOfFirst { it.instanceId == prevCurrentItem.instanceId }
          if (queueIndex !in upNextQueue.indices) {
            LOG.e { +it("Couldn't find item %s in upNextQueue", prevCurrentItem.title) }
            queueIndex = queueIndex.coerceIn(upNextQueue.indices)
          }
          newShuffled = mutableListOf()
        }

        persistIfCurrentUnchanged(
          newQueue = upNextQueue,
          newShuffled = newShuffled,
          prevCurrent = prevCurrentItem,
          prevCurrentIndex = prevCurrentIndex,
          skipUnchangedCheck = true
        )
        establishNewQueues(upNextQueue, newShuffled, queueIndex)
      }
    }
  }

  override fun goToQueueItem(instanceId: InstanceId) =
    goToIndexMaybePlay(queue.indexOfFirst { it.instanceId == instanceId })

  override fun handleScreenAction(action: ScreenAction, keyguardLocked: Boolean) {
    when (action) {
      ScreenAction.On -> if (keyguardLocked) maybeStartLockScreenActivity()
      ScreenAction.Off -> maybeStartLockScreenActivity()
    }
  }

  private fun maybeStartLockScreenActivity() {
    LockScreenActivity.maybeStart(Toque.appContext, appPrefs, currentItem.isPlaying)
  }

  override fun ifActiveRefreshMediaState() {
    if (isActive.value) updateMetadata()
  }

  private fun queueContainsMedia(): Boolean = queue.isNotEmpty()
  private fun atEndOfQueue(): Boolean = currentItemIndex >= queue.size - 1

  private suspend fun nextSong(
    playNow: PlayNow,
    transitionType: TransitionType
  ): Boolean {
    var success = false
    if (queueContainsMedia()) {
      if (!atEndOfQueue()) {
        doGoToIndex(currentItemIndex + 1, playNow, transitionType)
        success = true
      } else {
        if (repeat.queue) {
          doGoToIndex(0, playNow, transitionType)
          success = true
        } else {
          val nextList = getNextCategory()
          if (nextList.isNotEmpty) {
            playNext(
              nextList,
              ClearQueue(true),
              playNow,
              transitionType
            )
            success = true
          }
        }
      }
    }
    return success
  }

  private suspend fun getNextCategory(): CategoryMediaList =
    CategoryToken(prefs.lastCategoryType(), prefs.lastCategoryId())
      .getNextCategory(appPrefs.endOfQueueAction(), lastShuffle, audioMediaDao)

  private suspend fun getPreviousCategory(token: CategoryToken): CategoryMediaList =
    audioMediaDao.getPreviousCategory(token, shuffleLists)

  private fun doGoToIndex(
    goToIndex: Int,
    ensureSongPlays: PlayNow,
    transitionType: TransitionType
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
      transitionToNext(current, nextItem, select(transitionType), ensureSongPlays, Millis(0))
    } else {
      updateMetadata()
    }
  }

  private fun replaceCurrentItemInQueue(
    index: Int,
    item: PlayableAudioItem,
    replacement: PlayableAudioItem
  ) {
    if (shuffleMedia.value) {
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
    position: Millis
  ) {
    if (currentItem.isValid) {
      currentItemFlowJob?.cancel()
      currentItemFlowJob = null
    }
    if (playNow.value) currentItem.shutdown(transition.exitTransition) else currentItem.shutdown()
    currentItemFlowJob = nextItem.collectEvents(doOnStart = {
      nextItem.prepareSeekMaybePlay(
        position,
        if (playNow.value) transition.enterTransition else NoOpPlayerTransition,
        playNow
      )
    })
  }

  private fun PlayableAudioItem.collectEvents(doOnStart: () -> Unit) = eventFlow
    .onStart { doOnStart() }
    .onEach { event -> handleAudioItemEvent(event) }
    .catch { cause -> LOG.e(cause) { it("Event flow error: %s%s", id, title) } }
    .onCompletion { LOG._i { it("Event flow completed %s%s", id, title) } }
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
      is PlayableItemEvent.None -> Unit
    }
  }

  private suspend fun onPrepared(event: PlayableItemEvent.Prepared) {
    isActive.value = true
    val item = event.audioItem
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = event.position))
    updateMetadata()
    updatePlaybackState(if (item.isPlaying) PlayState.Playing else PlayState.Paused)

    scope.launch {
      val nextTitle = getNextMediaTitle()
//      getNotifier().postNextMediaTitleChanged(NextMediaTitleChangedEvent(nextTitle)) TODO
    }
  }

  private suspend fun onPositionUpdate(event: PlayableItemEvent.PositionUpdate) {
    updatePositionState(
      positionState.copy(mediaId = event.audioItem.id, playbackPosition = event.position)
    )
    if (event.audioItem.isPlaying) {
      updatePlaybackState(PlayState.Playing)
      if (event.shouldAutoAdvance()) {
        doNextOrRepeat()
      }
    } else {
      updatePlaybackState(PlayState.Paused)
    }
  }

  private fun onStart(event: PlayableItemEvent.Start) {
    playState = PlayState.Playing
    val item = event.audioItem
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = event.position))
    updatePlaybackState(PlayState.Playing)
  }

  private fun onPaused(event: PlayableItemEvent.Paused) {
    playState = PlayState.Paused
    val item = event.audioItem
    val position = event.position
    updatePositionState(positionState.copy(mediaId = item.id, playbackPosition = position))
    updatePlaybackState(PlayState.Paused)
  }

  private fun onStopped(event: PlayableItemEvent.Stopped) {
    val item = event.audioItem
    val current = currentItem
    if (current.instanceId == item.instanceId) {
      playState = PlayState.Stopped
      updatePlaybackState(PlayState.Stopped)
    }
  }

  private suspend fun onPlaybackCompleted(event: PlayableItemEvent.PlaybackComplete) {
    val item = event.audioItem
    val current = currentItem
    if (current.instanceId == item.instanceId) {
      if (!doNextOrRepeat()) {
        playState = PlayState.Paused
        doGoToIndex(0, PlayNow(false), Manual)
      }
    }
  }

  private suspend fun onError(event: PlayableItemEvent.Error) {
    val item = event.audioItem
    LOG.e { it("Playback error for item %s, %s", item.title.value, item.albumArtist.value) }
    nextSong(PlayNow(playState.isPlaying), Manual)
//    getNotifier().onError(item) TODO
  }

  private fun updatePlaybackState(playState: PlayState) =
    updatePlaybackState(currentItem, currentItemIndex, queue, playState)

  private fun updatePlaybackState(
    item: PlayableAudioItem,
    index: Int,
    queue: List<PlayableAudioItem>,
    playState: PlayState
  ) {
    updateQueueState("updatePlaybackState") { state ->
      state.copy(
        queue = queue.asLocalAudioItemList,
        queueIndex = index,
        position = positionState.playbackPosition,
        duration = item.duration,
        playingState = playState
      )
    }
    sessionState.setPlaybackState(
      PlaybackState(
        playState,
        positionState.playbackPosition,
        PlaybackRate.NORMAL,
        item.instanceId,
        PlaybackActions(
          hasMedia = queue.isNotEmpty(),
          isPlaying = playState == PlayState.Playing,
          hasPrev = index > 0 || positionState.playbackPosition > Millis.FIVE_SECONDS
        )
      )
    )
  }

  private fun updateMetadata() {
    sessionState.setMetadata(currentItem.metadata)
  }

  private fun updatePositionState(newState: QueuePositionState) {
    positionState = newState
    queuePositionStateDao.persistState(newState)
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
      FocusReaction.Play -> play(AllowFade)
      FocusReaction.Pause -> pause(NoFade)
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
      DuckAction.Pause -> currentItem.pause(NoFade)
      DuckAction.None -> Unit
    }
  }

  private fun endDuck() {
    val state = sharedPlayerState.duckedState
    sharedPlayerState.duckedState = DuckAction.None
    when (state) {
      DuckAction.Duck -> currentItem.endDuck()
      DuckAction.Pause -> currentItem.play(NoFade)
      DuckAction.None -> Unit
    }
  }

  private suspend fun doNextOrRepeat(): Boolean = repeat.current.alsoIf {
    doGoToIndex(currentItemIndex, PlayNow(true), TransitionType.AutoAdvance)
  } || nextSong(PlayNow(true), TransitionType.AutoAdvance)

  private fun canAutoAdvance(): Boolean = !atEndOfQueue() || appPrefs.endOfQueueAction().canAdvance

  private fun PlayableItemEvent.PositionUpdate.shouldAutoAdvance() = canAutoAdvance() &&
    audioItem.supportsFade && shouldAutoAdvanceFromPosition(position, duration)

  private fun shouldAutoAdvanceFromPosition(
    position: Millis,
    duration: Duration
  ) = appPrefs.autoAdvanceFade() && appPrefs.autoAdvanceFadeDuration().let { fadeLength ->
    duration > fadeLength && duration - position.toDuration() < fadeLength + OFFSET_CONSIDERED_END
  }

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
    updateQueueState("establishNewQueues") { state ->
      state.copy(
        queue = activeQueue.asLocalAudioItemList,
        queueIndex = index,
        position = item.position,
        duration = item.duration,
        playingState = playState,
      )
    }
    setSessionQueue(activeQueue)
  }

  private fun CategoryMediaList.setAsLastList() = token.write { songListType, id ->
    prefs.asyncEdit {
      it[lastCategoryType] = songListType
      it[lastCategoryId] = id
    }
  }

  private suspend fun addItemsToQueues(
    currentQueue: QueueList,
    currentShuffled: QueueList,
    prevCurrent: PlayableAudioItem,
    prevCurrentIndex: Int,
    idList: LongCollection,
    addAt: AddAt,
    clearAndRemakeQueues: Boolean,
    allowDuplicates: AllowDuplicates,
    shuffleMedia: ShuffleMedia
  ): Triple<QueueList, QueueList, Int> = withContext(Dispatchers.Default) {
    val newQueue: QueueList
    val newShuffled: QueueList
    val newIndex: Int

    val newQueueItems =
      itemFactory.makeNewQueueItems(idList, focusRequest, sharedPlayerState)

    if (clearAndRemakeQueues) {
      // if the queue is empty OR we are supposed to clear the queue, just build new list(s)
      newQueue = newQueueItems
      newShuffled = maybeMakeShuffled(shuffleMedia, newQueue)
      newIndex = 0
    } else {
      val idSet = LongOpenHashSet(idList)
      if (shuffleMedia.value) {
        val queueInfo = currentShuffled.addNewItems(
          newQueueItems,
          prevCurrentIndex,
          prevCurrent,
          idSet,
          allowDuplicates,
          addAt,
          shuffleMedia
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
          shuffleMedia
        )
        newQueue = queueInfo.queue
        newIndex = queueInfo.index
        newShuffled = mutableListOf()
      }
    }
    Triple(newQueue, newShuffled, newIndex)
  }

  private fun maybeMakeShuffled(shuffleMedia: ShuffleMedia, newQueue: QueueList): QueueList =
    if (shuffleMedia.value) newQueue.shuffled() else emptyList()

  private suspend fun persistIfCurrentUnchanged(
    newQueue: QueueList,
    newShuffled: QueueList,
    prevCurrent: PlayableAudioItem,
    prevCurrentIndex: Int,
    updateTime: Millis = Millis.currentUtcEpochMillis(),
    skipUnchangedCheck: Boolean = false
  ) {
    if (skipUnchangedCheck) {
      queueDao.replaceQueueItems(AudioMediaDao.QUEUE_ID, newQueue, newShuffled, updateTime)
    } else if (currentItemIsStill(prevCurrent, prevCurrentIndex)) {
      queueDao.replaceQueueItems(AudioMediaDao.QUEUE_ID, newQueue, newShuffled, updateTime)
      if (!currentItemIsStill(prevCurrent, prevCurrentIndex)) {
        // This is effectively a rollback
        queueDao.replaceQueueItems(
          AudioMediaDao.QUEUE_ID,
          upNextQueue,
          upNextShuffled,
          updateTime
        )
        throw QueueChangedException("Changed after persisting")
      }
    } else throw QueueChangedException("Changed before persisting")
  }

  private suspend fun persistIfQueueNotUpdated(
    newQueue: MutableList<PlayableAudioItem>,
    newShuffled: MutableList<PlayableAudioItem>,
    lastUpdated: Millis
  ) {
    queueDao.replaceIfQueueUnchanged(AudioMediaDao.QUEUE_ID, newQueue, newShuffled, lastUpdated)
      .onFailure { msg ->
        val str = msg.toString()
        LOG.e { it(str) }
        throw QueueChangedException(str)
      }
  }

  private fun currentItemIsStill(prevCurrent: PlayableAudioItem, newIndex: Int): Boolean =
    prevCurrent.isNotValid || queue.itemIsAtIndex(prevCurrent, newIndex)
}

private fun LongCollection.removeItemIf(item: Long, remove: Boolean): LongCollection = apply {
  if (remove) rem(item)
}

private fun LongList.removeDuplicatesIf(remove: Boolean): LongCollection =
  if (remove) LongLinkedOpenHashSet(this) else this

/**
 * From Roman Elizarov https://stackoverflow.com/a/46890009/2660904
 *
 * Calls [block] up to [times] given an [initialDelay], a [maxDelay], and a back off [factor]. The
 * [block] is passed true on the the last retry if [times] retries are necessary.
 *
 * This is used in the rare case the queue is changed in the middle of updating. The operation of
 * [block] failed with an exception, but can be retried. If there queue activity, such as removing
 * an item from the queue, at the same time playback has reached end of queue, it's possible changes
 * may be attempted at the same time. The queue should only be changed on the UI thread and
 * clients typically come through the [LocalAudioCommandProcessor] which enqueues requests, but
 * some queue updates are internal and due to playback - so won't be queued.
 */
private suspend fun <T> retryQueueChange(
  times: Int = MAX_QUEUE_RETRIES,
  initialDelay: Millis = Millis(100),
  maxDelay: Millis = Millis(800),
  factor: Long = 2,
  block: suspend (Boolean) -> T
): T {
  var currentDelay = initialDelay
  repeat(times - 1) { index ->
    try {
      return block(false)
    } catch (e: QueueChangedException) {
      LOG.e(e) { it("Queue changed during update. Retry=$index") }
    }
    delay(currentDelay())
    currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)
  }
  return block(true)
}

private fun List<PlayableAudioItem>.itemIsAtIndex(item: PlayableAudioItem, index: Int): Boolean =
  if (index in indices) this[index].instanceId == item.instanceId else false

/**
 * Add [newQueueItems] into this list after [currentIndex] or at the end of the list depending on
 * [addAt], removing all items being added from queue history if ![allowDuplicates] using
 * [newItemsIdSet], and adjusting the index as necessary. If [shuffleMedia] then the new
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
  shuffleMedia: ShuffleMedia,
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
      addAt.addTo(this, afterCurrent, newQueueItems, shuffleMedia, shuffler)
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

private fun MutableList<PlayableAudioItem>.removeWithInstanceId(instanceId: InstanceId) {
  removeAt(indexOfFirst { it.instanceId == instanceId })
}

private val EndOfQueueAction.canAdvance: Boolean get() = this !== EndOfQueueAction.Stop

private data class LocalAudioItem(
  override val id: MediaId,
  override val title: Title,
  override val albumTitle: AlbumTitle,
  override val albumArtist: ArtistName,
  override val artist: ArtistName,
  override val duration: Duration,
  override val trackNumber: Int,
  override val localAlbumArt: Uri,
  override val albumArt: Uri,
  override val rating: Rating,
  override val location: Uri,
  override val fileUri: Uri,
  override val albumId: AlbumId,
  override val instanceId: InstanceId
) : AudioItem

private val PlayableAudioItem.asLocalAudioItem: AudioItem
  get() = LocalAudioItem(
    id = id,
    title = title,
    albumTitle = albumTitle,
    albumArtist = albumArtist,
    artist = artist,
    duration = duration,
    trackNumber = trackNumber,
    localAlbumArt = localAlbumArt,
    albumArt = albumArt,
    rating = rating,
    location = location,
    fileUri = fileUri,
    albumId = albumId,
    instanceId = instanceId
  )

private val List<PlayableAudioItem>.asLocalAudioItemList: List<AudioItem>
  get() = map { playableAudioItem -> playableAudioItem.asLocalAudioItem }
