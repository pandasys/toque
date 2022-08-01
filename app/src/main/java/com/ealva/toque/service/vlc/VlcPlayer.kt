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

package com.ealva.toque.service.vlc

import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.Micros
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.common.asMillis
import com.ealva.toque.log._e
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.audio.SharedPlayerState
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayerEvent
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.player.WakeLock
import com.ealva.toque.service.queue.MayFade
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

private val LOG by lazyLogger(VlcPlayer::class)

/**
 * Only 2 VlcPlayers may be active at any given time. This allows for fade in/out functionality.
 * There is a queue of players, [VlcPlayerImpl.Companion.fifoPlayerQueue], that has max 2 players.
 * During construction the player calls [VlcPlayerImpl.Companion.addToQueue] to add itself and
 * during shutdown the player calls [VlcPlayerImpl.Companion.removeFromQueue]. These functions
 * handle ensuring only 2 players active at a given time and any "excess" players are removed
 * from the end of the queue and shutdown.
 */
interface VlcPlayer : AvPlayer {
  fun getVlcMedia(): VlcMedia

  companion object {
    operator fun invoke(
      id: MediaId,
      albumId: AlbumId,
      media: IMedia,
      title: Title,
      duration: Duration,
      sharedPlayerState: SharedPlayerState,
      onPreparedTransition: PlayerTransition,
      prefs: AppPrefs,
      requestFocus: AvPlayer.FocusRequest,
      wakeLock: WakeLock,
      dispatcher: CoroutineDispatcher
    ): VlcPlayer {
      // kick off the search for a preset so maybe it's ready by the time we collect the flow
      sharedPlayerState.setPreferred(id, albumId)
      return VlcPlayerImpl(
        id,
        albumId,
        media,
        title,
        sharedPlayerState,
        duration,
        onPreparedTransition,
        prefs,
        requestFocus,
        wakeLock,
        dispatcher
      )
    }
  }
}

/**
 * To understand a few parts of this implementation it's important to understand that this Player
 * will delegate some operations to [PlayerTransition]s and PlayerTransitions also use a
 * [TransitionPlayer] interface instead of calling this implementation directly. This provides such
 * functionality as the user pressing pause, immediately seeing the play/pause button change state
 * due to an emitted event, yet the underlying player is still fading out the audio. Another
 * scenario is cross fading from one song into another - the user sees the new song start playing in
 * the UI yet another song is fading out at the same time. The TransitionPlayer implementation is an
 * inner class that bypasses some code other clients would use and may report some state to the
 * PlayerTransition implementation which is different than what other clients would see.
 */
private class VlcPlayerImpl(
  private val id: MediaId,
  private val albumId: AlbumId,
  vlcMedia: IMedia,
  @Suppress("unused") private val title: Title, // for logging
  private val playerState: SharedPlayerState,
  override var duration: Duration,
  onPreparedTransition: PlayerTransition,
  private val appPrefs: AppPrefs,
  private val requestFocus: AvPlayer.FocusRequest,
  private val wakeLock: WakeLock,
  dispatcher: CoroutineDispatcher
) : VlcPlayer {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val transitionPlayer = TheTransitionPlayer()
  private var transition: PlayerTransition = NoOpPlayerTransition
  private val onPrepared: PlayerTransition = getOnPrepared(onPreparedTransition)
  private var shutdownOnPrepared = false
  private var unmutedVolume = Volume.NONE
  private var firstStart: Boolean = true
  private var allowPositionUpdate = true
  private val mediaPlayer: MediaPlayer

  init {
    addToQueue(this)
    mediaPlayer = vlcMedia.makePlayer()

    setAudioOutput(playerState.outputModule.value)

    playerState.currentPreset
      .onEach { preset -> if (!isShutdown) preset.asVlcPreset().applyToPlayer(mediaPlayer) }
      .launchIn(scope)

    // We should have a currentPreset emission for the "current" outputRoute, so drop the first.
    // While the lookup for a preferred preset occurs on another thread, no reason to churn for
    // something that won't be emitted.
    playerState.outputRoute
      .drop(1)
      .onEach { playerState.setPreferred(id, albumId) }
      .launchIn(scope)

    // We should have a currentPreset emission for the "current" eqMode, so drop the first.
    // While the lookup for a preferred preset occurs on another thread, no reason to churn for
    // something that won't be emitted.
    playerState.eqMode
      .drop(1)
      .onEach { playerState.setPreferred(id, albumId) }
      .launchIn(scope)

    playerState.playbackRate
      .onEach { mediaPlayer.rate = it.value }
      .launchIn(scope)
  }

  override fun getVlcMedia(): VlcMedia = VlcMedia(requireNotNull(mediaPlayer.media))

  override val eventFlow = MutableSharedFlow<AvPlayerEvent>(extraBufferCapacity = 10)

  private var seekable = false
  override val isSeekable: Boolean
    get() = seekable

  private var pausable = false
  override val isPausable: Boolean
    get() = pausable

  private var prepared = false

  override val isValid: Boolean
    get() = prepared && !isShutdown

  override val time: Millis
    get() = mediaPlayer.time.asMillis

  override val isPlaying: Boolean
    get() = if (prepared) transition.isPlaying else onPrepared.isPlaying

  override val isPaused: Boolean
    get() = if (prepared) transition.isPaused else onPrepared.isPaused

  override var isShutdown = false

  override fun toString(): String = """VlcPlayer[
    |   isActive=${scope.isActive},
    |   id=${id.value},
    |   isValid=$isValid
    |   mediaPlayer.isReleased=${mediaPlayer.isReleased}
    |   ]""".trimMargin()

  private var realVolume = Volume.NONE
  override var volume: Volume
    get() = realVolume
    set(value) {
      realVolume = value.coerceIn(AvPlayer.DEFAULT_VOLUME_RANGE)
      muted = false
      unmutedVolume = realVolume
      if (isValid) {
        mediaPlayer.setMappedVolume(
          if (playerState.ducked) realVolume.coerceAtMost(appPrefs.duckVolume()) else realVolume
        )
      }
    }

  private var muted = false
  override var isMuted: Boolean
    get() = muted
    set(mute) {
      if (isValid) {
        muted = mute
        if (mute)
          mediaPlayer.setMappedVolume(Volume.NONE) else mediaPlayer.setMappedVolume(unmutedVolume)
      }
    }

  override var playbackRate: PlaybackRate
    get() = PlaybackRate(mediaPlayer.rate).coerceIn(PlaybackRate.RANGE)
    set(rate) {
      mediaPlayer.rate = rate.coerceIn(PlaybackRate.RANGE).value
    }

  override var audioDelay: Micros
    get() = Micros(mediaPlayer.audioDelay)
    set(delay) {
      mediaPlayer.audioDelay = delay()
    }
  override var spuDelay: Micros
    get() = Micros(mediaPlayer.spuDelay)
    set(delay) {
      mediaPlayer.spuDelay = delay()
    }

  override val isVideoPlayer: Boolean
    get() = isValid && mediaPlayer.videoTracksCount > 0
  override val isAudioPlayer: Boolean
    get() = isValid && !isVideoPlayer && mediaPlayer.audioTracksCount > 0

  private val pauseTransition: PlayerTransition
    get() = if (appPrefs.playPauseFade()) {
      PauseFadeOutTransition(appPrefs.playPauseFadeDuration())
    } else PauseImmediateTransition()

  private val playTransition: PlayerTransition
    get() = if (appPrefs.playPauseFade()) {
      FadeInTransition(appPrefs.playPauseFadeDuration())
    } else PlayImmediateTransition()

  override fun playStartPaused() {
    mediaPlayer.play()
  }

  override fun play(mayFade: MayFade) {
    if (isValid && requestFocus.requestFocus()) {
      startTransition(getPlayTransition(mayFade), false)
    }
  }

  override fun pause(mayFade: MayFade) {
    if (isValid) startTransition(getPauseTransition(mayFade), false)
  }

  @OptIn(ExperimentalTime::class)
  override fun seek(position: Millis) {
    if (isValid && mediaPlayer.isSeekable) {
      allowPositionUpdate = false
      /*
      if media is playing it will update position and we can't control order of updates given
      async nature. So we'll stop allowing position update events, send one for the seek, and then
      allow position updates again. If the media player is paused it doesn't send position updates
      so disallowing updates is only for scenario where media is playing.
      */
      mediaPlayer.time = position.value
      tryEmit(AvPlayerEvent.PositionUpdate(position, duration, false))
      allowPositionUpdate = true
    }
  }

  override fun stop() {
    if (isValid) mediaPlayer.stop()
  }

  /**
   * [shutdownOnPrepared] is always set to true to avoid any possible timing issues introduced by a
   * VlcPlayer client. If separate threads are involved, it would be possible for [prepared] to be
   * set immediately after we checked and found it false, which would cause [secondStageShutdown] to
   * be missed.
   */
  override fun shutdown() {
    if (!isShutdown) {
      isShutdown = true
      transition.setCancelled()
      seekable = false
      shutdownOnPrepared = true
      if (prepared) secondStageShutdown()
    }
  }

  /**
   * This function must be idempotent, so no side effects from calling more than once
   */
  private fun secondStageShutdown() {
    removeFromQueue(this)
    if (!mediaPlayer.isReleased) mediaPlayer.release()
    wakeLock.release()
    scope.cancel()
  }

  override fun duck() {
    if (!transition.isActive) {
      volume = appPrefs.duckVolume()
    }
  }

  override fun endDuck() {
    if (!transition.isActive) volume = Volume.MAX
  }

  override fun transitionTo(transition: PlayerTransition) {
    startTransition(transition, false)
  }

  private fun getPauseTransition(mayFade: MayFade) =
    if (mayFade.noFade) PauseImmediateTransition() else pauseTransition

  private fun getPlayTransition(mayFade: MayFade) =
    if (mayFade.noFade) PlayImmediateTransition() else playTransition

  private fun setAudioOutput(module: AudioOutputModule) {
    mediaPlayer.setAudioOutput(module.toString())
  }

  private fun tryEmit(event: AvPlayerEvent) {
    if (!eventFlow.tryEmit(event)) {
      scope.launch { eventFlow.emit(event) }
    }
  }

  private suspend fun emit(event: AvPlayerEvent) {
    eventFlow.emit(event)
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event: MediaPlayer.Event ->
//    LOG._e { it("%s %s", title(), event.asString) }
    if (!isShutdown) {
      scope.launch {
        when (event.type) {
          MediaPlayer.Event.Opening -> mediaPlayer.setMappedVolume(realVolume)
          MediaPlayer.Event.Buffering -> if (!prepared && event.ampleBufferedForPrepare) {
            prepared = true
            startTransition(onPrepared, notifyPrepared = true)
          }
          MediaPlayer.Event.Playing -> if (prepared) wakeLock.acquire()
          MediaPlayer.Event.Paused -> if (prepared) wakeLock.release()
          MediaPlayer.Event.Stopped -> {
            wakeLock.release()
            seekable = false
            emit(AvPlayerEvent.Stopped)
          }
          MediaPlayer.Event.EndReached -> {
            // emit before shutdown or won't go due to scope cancellation
            emit(AvPlayerEvent.PlaybackComplete)
            shutdown()
          }
          MediaPlayer.Event.EncounteredError -> {
            prepared = false
            // emit before shutdown or won't go due to scope cancellation
            emit(AvPlayerEvent.Error)
            shutdown()
          }
          MediaPlayer.Event.TimeChanged -> if (allowPositionUpdate) {
            emit(AvPlayerEvent.PositionUpdate(Millis(event.timeChanged), duration, true))
          }
          MediaPlayer.Event.PositionChanged -> Unit
          MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
          MediaPlayer.Event.PausableChanged -> {
            pausable = event.pausable
            val reportedLength = mediaPlayer.length
            if (reportedLength > 0) {
              duration = reportedLength.toDuration(DurationUnit.MILLISECONDS)
            }
          }
          MediaPlayer.Event.LengthChanged -> Unit
        }
      }
    } else {
      /* It's possible to receive a request to shutdown before even being prepared if transitions
      are happening very quickly. isShutdown will be set to true to prevent any other activity,
      but if shutdownOnPrepared = true and ensure secondStageShutdown is called to stop any player
      activity and free resources. secondStageShutdown must be idempotent
       */
      if (shutdownOnPrepared) {
        secondStageShutdown()
      }
    }
  }

  private fun getOnPrepared(transition: PlayerTransition) = transition.apply {
    setPlayer(transitionPlayer)
  }

  private fun IMedia.makePlayer(): MediaPlayer = MediaPlayer(this).apply {
    setEventListener(makeEventListener())
  }

  private fun startTransition(newTransition: PlayerTransition, notifyPrepared: Boolean = false) {
    if (!isShutdown && transition.accept(newTransition)) {
      transition.setCancelled()
      newTransition.setPlayer(transitionPlayer)
      transition = newTransition
      if (notifyPrepared) tryEmit(AvPlayerEvent.Prepared(Millis(mediaPlayer.time), duration))
      newTransition.execute()
    } else {
      LOG._e { it("%s illegal transition %s to %s", title, transition, newTransition) }
    }
  }

  /**
   * This class should not typically call [VlcPlayer] methods as they may cause transitions which in
   * turn call this player = blowing up the stack. [VlcPlayer.shutdown] being the exception as it
   * will immediate cancel any transitions and shutdown the player.
   */
  private inner class TheTransitionPlayer : TransitionPlayer {
    override val mediaTitle: Title get() = title
    override val isPaused: Boolean get() = isValid && !mediaPlayer.isPlaying
    override val isPlaying: Boolean get() = isValid && mediaPlayer.isPlaying
    override val playerIsShutdown: Boolean get() = isShutdown

    override var playerVolume: Volume
      get() = volume
      set(value) {
        if (_allowVolumeChange) volume = value
      }
    override val volumeRange: VolumeRange get() = AvPlayer.DEFAULT_VOLUME_RANGE

    private var _allowVolumeChange = true
    override var allowVolumeChange: Boolean
      get() = _allowVolumeChange
      set(value) {
        _allowVolumeChange = value
      }

    override val remainingTime: Duration
      get() = if (isValid) duration - time.toDuration() else Duration.ZERO

    override fun notifyPaused() = tryEmit(AvPlayerEvent.Paused(time))

    override fun notifyPlaying() {
      val isFirstStart = firstStart
      firstStart = false
      tryEmit(AvPlayerEvent.Start(isFirstStart))
    }

    /**
     * Don't call [VlcPlayer.pause] from here. See [play] docs
     */
    override fun pause() {
      if (isValid) mediaPlayer.pause()
    }

    /**
     * This is called by PlayerTransition implementations, so don't call [VlcPlayer.play] as it
     * will start a Transition = indirect recursion/out of stack
     */
    override fun play() {
      if (isValid && requestFocus.requestFocus()) mediaPlayer.play()
    }

    override fun shutdownPlayer() = shutdown()

//    override fun shouldContinue(): Boolean =
//      !playerIsShutdown &&
//        duration > Duration.ZERO &&
//        duration.asMillis() - mediaPlayer.time > OFFSET_CONSIDERED_END
  }

  /**
   * The [fifoPlayerQueue] is used to hold VlcPlayer implementations and to limit the number of
   * active players. We also want to ensure players release underlying resources as quickly as
   * possible. If there are rapid transitions between players which are playing, it's important to
   * shut them down fast so that there are no audible artifacts, regardless of any transitions (fade
   * in/fade out) which may be occurring.
   *
   * One scenario is where the user has selected cross fading and one song is fading out while
   * another is fading in. This cross fade lasts seconds and during this time the user could
   * press next song again. The user could do this rapidly. We want to ensure audio is quickly
   * stopped when a particular player is no longer in use.
   */
  companion object {
    private val fifoPlayerQueue = ArrayDeque<VlcPlayer>(3)
    private val queueLock = ReentrantLock(true)

    private fun addToQueue(player: VlcPlayer) = queueLock.withLock {
      while (fifoPlayerQueue.size > 1) {
        fifoPlayerQueue.removeLast().shutdown()
      }
      fifoPlayerQueue.addFirst(player)
    }

    /**
     * Function is idempotent. Once the player is removed from the queue, subsequent calls
     * are ignored.
     */
    private fun removeFromQueue(player: VlcPlayer) = queueLock.withLock {
      // if player not in queue don't do anything else
      if (fifoPlayerQueue.remove(player)) {
        while (fifoPlayerQueue.size > 1) {
          fifoPlayerQueue.removeLast().shutdown()
          LOG.w { it("removeFromQueue queue size > 1") }
        }
      }
    }
  }
}

private fun SharedPlayerState.EqPresetBandData.asVlcPreset(): VlcEqPreset =
  if (eqPreset.isNone) VlcEqPreset.NONE else eqPreset as VlcEqPreset

@Suppress("unused")
private val MediaPlayer.Event.asString: String
  get() {
    return when (type) {
      MediaPlayer.Event.MediaChanged -> "Event.MediaChanged"
      MediaPlayer.Event.Opening -> "Event.Opening"
      MediaPlayer.Event.Buffering -> "Event.Buffering $buffering"
      MediaPlayer.Event.Playing -> "Event.Playing"
      MediaPlayer.Event.Paused -> "Event.Paused"
      MediaPlayer.Event.Stopped -> "Event.Stopped"
      MediaPlayer.Event.EndReached -> "Event.EndReached"
      MediaPlayer.Event.EncounteredError -> "Event.EncounteredError"
      MediaPlayer.Event.TimeChanged -> "Event.TimeChanged $timeChanged"
      MediaPlayer.Event.PositionChanged -> "Event.PositionChanged $positionChanged"
      MediaPlayer.Event.SeekableChanged -> "Event.SeekableChanged $seekable"
      MediaPlayer.Event.PausableChanged -> "Event.PausableChanged $pausable"
      MediaPlayer.Event.LengthChanged -> "Event.LengthChanged $lengthChanged"
      MediaPlayer.Event.Vout -> "Event.Vout $voutCount"
      MediaPlayer.Event.ESAdded -> "Event.ESAdded"
      MediaPlayer.Event.ESDeleted -> "Event.ESDeleted"
      MediaPlayer.Event.ESSelected -> "Event.ESSelected"
      MediaPlayer.Event.RecordChanged -> "Event.RecordChanged"
      else -> "Event.UNKNOWN"
    }
  }

private const val BUFFERING_PERCENT_TRIGGER_PREPARED = 50.0

private inline val MediaPlayer.Event.ampleBufferedForPrepare: Boolean
  get() = buffering > BUFFERING_PERCENT_TRIGGER_PREPARED

/**
 * Map the volume value used by the MediaPlayer to a value which better matches human hearing. As of
 * now the user does not directly affect this volume. The user only affects the stream volume
 * (Media volume presented to the user) and this volume is only affected via transitions.
 */
private fun MediaPlayer.setMappedVolume(linearVolume: Volume) {
  volume = linearToLogVolumeMap[linearVolume().coerceIn(linearToLogVolumeMap.indices)]
}

/**
 * Linear volume controls are poor because that's not how our hearing works. This map converts the
 * linear volume scale the media player uses to a logarithmic scale our ears use.
 * [Decibels](https://en.wikipedia.org/wiki/Decibel)
 *
 * To hear the problem change the [MediaPlayer.setMappedVolume] to directly use the linear volume
 * without mapping, turn the device volume up (doesn't need to be very loud), and use transition
 * fades: PauseFadeOutTransition, ShutdownFadeOutTransition, FadeInTransition, etc... On fade out
 * you should hear the volume decrease much too quickly and be silent for too long. Fade in sounds
 * very sudden too.
 *
 * Currently no reason to calculate this at runtime as there are only 101 distinct values, so this
 * function was used to calculate these values:
 * ```
 * fun linearToLog(z: Int): Int {
 *   val x = 0.0
 *   val y = 100.00
 *   val b = (if (x > 0) ln(y / x) else ln(y)) / (y - x)
 *   val a = 100 / exp(b * 100)
 *   return (a * exp(b * z)).roundToInt()
 * }```
 */
private val linearToLogVolumeMap = intArrayOf(
  0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4,
  5, 5, 5, 5, 5, 6, 6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 10, 10, 10, 11, 11, 12, 13, 13, 14, 14, 15, 16,
  17, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 32, 33, 35, 36, 38, 40, 42, 44, 46, 48,
  50, 52, 55, 58, 60, 63, 66, 69, 72, 76, 79, 83, 87, 91, 95, 100
)
