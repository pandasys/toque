/*
 * Copyright 2020 eAlva.com
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
import com.ealva.toque.audioout.AudioOutputModule
import com.ealva.toque.common.Micros
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.log._e
import com.ealva.toque.log._w
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.audio.SharedPlayerState
import com.ealva.toque.service.player.AvPlayer
import com.ealva.toque.service.player.AvPlayer.Companion.OFFSET_CONSIDERED_END
import com.ealva.toque.service.player.AvPlayerEvent
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.NoOpPlayerTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.TransitionPlayer
import com.ealva.toque.service.player.WakeLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(VlcPlayer::class)

interface VlcPlayer : AvPlayer {

  companion object {
    operator fun invoke(
      id: MediaId,
      albumId: AlbumId,
      media: IMedia,
      title: Title,
      duration: Millis,
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
      ).apply {
        VlcPlayerImpl.addToQueue(this)
      }
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
  @Suppress("unused") private val title: Title,
  private val sharedPlayerState: SharedPlayerState,
  override var duration: Millis,
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
  private val mediaPlayer: MediaPlayer = vlcMedia.makePlayer()

  init {
    setAudioOutput(sharedPlayerState.outputModule.value)

    sharedPlayerState.currentPreset
      .onEach { preset -> if (!isShutdown) preset.asVlcPreset().applyToPlayer(mediaPlayer) }
      .launchIn(scope)

    // We should have a currentPreset emission for the "current" outputRoute, so drop the first.
    // While the lookup for a preferred preset occurs on another thread, no reason to churn for
    // something that won't be emitted.
    sharedPlayerState.outputRoute
      .drop(1)
      .onEach { sharedPlayerState.setPreferred(id, albumId) }
      .launchIn(scope)

    // We should have a currentPreset emission for the "current" eqMode, so drop the first.
    // While the lookup for a preferred preset occurs on another thread, no reason to churn for
    // something that won't be emitted.
    sharedPlayerState.eqMode
      .drop(1)
      .onEach { sharedPlayerState.setPreferred(id, albumId) }
      .launchIn(scope)

    sharedPlayerState.playbackRate
      .onEach { mediaPlayer.rate = it.value }
      .launchIn(scope)
  }

  override val eventFlow = MutableSharedFlow<AvPlayerEvent>(extraBufferCapacity = 30)

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
    get() = Millis(mediaPlayer.time)

  override val isPlaying: Boolean
    get() = if (prepared) transition.isPlaying else onPrepared.isPlaying

  override val isPaused: Boolean
    get() = if (prepared) transition.isPaused else onPrepared.isPaused

  override var isShutdown = false

  private var realVolume = Volume.NONE
  override var volume: Volume
    get() = realVolume
    set(value) {
      realVolume = value.coerceIn(AvPlayer.DEFAULT_VOLUME_RANGE)
      muted = false
      unmutedVolume = realVolume // MediaPlayer get volume not working
      if (isValid) mediaPlayer.setMappedVolume(
        if (sharedPlayerState.ducked) realVolume.coerceAtMost(appPrefs.duckVolume()) else realVolume
      )
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
      PauseFadeOutTransition(appPrefs.playPauseFadeLength())
    } else PauseImmediateTransition()

  private val playTransition: PlayerTransition
    get() = if (appPrefs.playPauseFade()) {
      FadeInTransition(appPrefs.playPauseFadeLength())
    } else PlayImmediateTransition()

  override fun playStartPaused() {
    mediaPlayer.play()
  }

  override fun play(immediate: Boolean) {
    LOG._e { it("play") }
    if (isValid && requestFocus.requestFocus()) {
      startTransition(getPlayTransition { immediate }, false)
    }
  }

  override fun pause(immediate: Boolean) {
    if (isValid) startTransition(getPauseTransition { immediate }, false)
  }

  override fun seek(position: Millis) {
    if (isValid && mediaPlayer.isSeekable) {
      /*
      if media is playing it will update position and we can't control order of updates given
      async nature. So we'll stop allowing position update events, send one for the seek, and then
      allow position updates again. If the media player is paused it doesn't send position
      updates so disallowing updates is only for scenario where media is playing.
      */
      allowPositionUpdate = false
      mediaPlayer.time = position.value
      val theDuration = duration
      scope.launch {
        emit(AvPlayerEvent.PositionUpdate(position, theDuration, false))
      }
      allowPositionUpdate = true
    }
  }

  override fun stop() {
    if (isValid) mediaPlayer.stop()
  }

  override fun shutdown() {
    if (!isShutdown) {
      isShutdown = true
      transition.setCancelled()
      seekable = false
      if (prepared) secondStageShutdown() else shutdownOnPrepared = true
    }
  }

  private fun secondStageShutdown() {
    scope.cancel()
    removeFromQueue(this)
    if (!mediaPlayer.isReleased) {
      mediaPlayer.release()
    }
    wakeLock.release()
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

  private inline fun getPauseTransition(immediate: () -> Boolean) =
    if (immediate()) PauseImmediateTransition() else pauseTransition

  private fun getPlayTransition(immediate: () -> Boolean) =
    if (immediate()) PlayImmediateTransition() else playTransition

  private fun setAudioOutput(module: AudioOutputModule) {
    LOG._e { it("setAudioOutput %s", module) }
    mediaPlayer.setAudioOutput(module.toString())
  }

  private suspend fun emit(event: AvPlayerEvent) {
    eventFlow.emit(event)
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
//    LOG._e { it("%s %s", title(), event.asString()) }
    if (!isShutdown) {
      scope.launch {
        when (event.type) {
          MediaPlayer.Event.Opening -> mediaPlayer.setMappedVolume(realVolume)
          MediaPlayer.Event.Buffering -> if (!prepared && event.ampleBufferedForPrepare()) {
            prepared = true
            startTransition(onPrepared, notifyPrepared = true)
          }
          MediaPlayer.Event.Playing -> if (prepared) wakeLock.acquire()
          MediaPlayer.Event.Paused -> if (prepared) wakeLock.release()
          MediaPlayer.Event.Stopped -> {
            wakeLock.release()
            seekable = false
            emit(AvPlayerEvent.Stopped(time))
          }
          MediaPlayer.Event.EndReached -> {
            LOG._e { it("endReached") }
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
          MediaPlayer.Event.TimeChanged -> {
            if (allowPositionUpdate) {
              emit(AvPlayerEvent.PositionUpdate(Millis(event.timeChanged), duration, true))
            }
          }
          MediaPlayer.Event.PositionChanged -> event.positionChanged
          MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
          MediaPlayer.Event.PausableChanged -> {
            pausable = event.pausable
            val reportedLength = mediaPlayer.length
            if (reportedLength > 0) {
              duration = Millis(reportedLength)
            }
          }
          MediaPlayer.Event.LengthChanged -> {

          }
        }
      }
    } else {
      if (!prepared && shutdownOnPrepared && event.isBuffering && event.ampleBufferedForPrepare()) {
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
    if (isSeekable && transition.accept(newTransition)) {
      transition.setCancelled()
      newTransition.setPlayer(transitionPlayer)
      transition = newTransition
      scope.launch {
        if (notifyPrepared) emit(AvPlayerEvent.Prepared(Millis(mediaPlayer.time), duration))
        newTransition.execute()
      }
    }
  }

  private inner class TheTransitionPlayer : TransitionPlayer {
    override val isPaused: Boolean get() = isValid && !mediaPlayer.isPlaying
    override val isPlaying: Boolean get() = isValid && mediaPlayer.isPlaying
    override val playerIsShutdown: Boolean get() = isShutdown

    override var playerVolume: Volume
      get() = volume
      set(value) {
        if (_allowVolumeChange) {
          volume = value
        }
      }
    override val volumeRange: VolumeRange get() = AvPlayer.DEFAULT_VOLUME_RANGE

    private var _allowVolumeChange = true
    override var allowVolumeChange: Boolean
      get() {
        return _allowVolumeChange
      }
      set(value) {
        _allowVolumeChange = value
      }

    override val remainingTime: Millis
      get() = if (isValid) duration - mediaPlayer.time else Millis(0)

    override fun notifyPaused() {
      scope.launch { emit(AvPlayerEvent.Paused(time)) }
    }

    override fun notifyPlaying() {
      val isFirstStart = firstStart
      scope.launch { emit(AvPlayerEvent.Start(isFirstStart)) }
      firstStart = false
    }

    override fun pause() {
      if (isValid) mediaPlayer.pause()
    }

    override fun play() {
      if (isValid) mediaPlayer.play()
    }

    override fun shutdownPlayer() = shutdown()

    override fun shouldContinue(): Boolean =
      !playerIsShutdown && duration > 0 && duration - mediaPlayer.time > OFFSET_CONSIDERED_END
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

    fun addToQueue(player: VlcPlayer) = queueLock.withLock {
      while (fifoPlayerQueue.size > 1) {
        fifoPlayerQueue.removeLast().shutdown()
      }
      fifoPlayerQueue.addFirst(player)
    }

    private fun removeFromQueue(player: VlcPlayer) = queueLock.withLock {
      fifoPlayerQueue.remove(player)
      while (fifoPlayerQueue.size > 1) {
        fifoPlayerQueue.removeLast().shutdown()
        LOG._w { it("removeFromQueue queue size > 1") }
      }
    }
  }
}
