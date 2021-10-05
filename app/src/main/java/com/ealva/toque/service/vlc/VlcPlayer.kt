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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Micros
import com.ealva.toque.common.Millis
import com.ealva.toque.common.PlaybackRate
import com.ealva.toque.common.Title
import com.ealva.toque.common.Volume
import com.ealva.toque.common.VolumeRange
import com.ealva.toque.log._w
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.media.EqPreset
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(VlcPlayer::class)

interface VlcPlayer : AvPlayer {

  companion object {
    operator fun invoke(
      media: IMedia,
      title: Title,
      duration: Millis,
      eqPreset: VlcEqPreset,
      onPreparedTransition: PlayerTransition,
      prefs: AppPrefs,
      wakeLock: WakeLock,
      dispatcher: CoroutineDispatcher
    ): VlcPlayer = VlcPlayerImpl(
      media,
      title,
      eqPreset,
      duration,
      onPreparedTransition,
      prefs,
      wakeLock,
      dispatcher
    ).apply {
      VlcPlayerImpl.addToQueue(this)
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
  vlcMedia: IMedia,
  @Suppress("unused") private val title: Title,
  private var eqPreset: VlcEqPreset,
  override var duration: Millis,
  onPreparedTransition: PlayerTransition,
  private val prefs: AppPrefs,
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

  override val equalizer: EqPreset
    get() = eqPreset
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
      if (isValid) mediaPlayer.setMappedVolume(realVolume)
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
    get() = if (prefs.playPauseFade()) {
      PauseFadeOutTransition(prefs.playPauseFadeLength())
    } else PauseImmediateTransition()

  private val playTransition: PlayerTransition
    get() = if (prefs.playPauseFade()) {
      FadeInTransition(prefs.playPauseFadeLength())
    } else PlayImmediateTransition()

  override fun playStartPaused() {
    mediaPlayer.play()
  }

  override fun play(immediate: Boolean) {
    if (isValid) startTransition(getPlayTransition { immediate }, false)
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
      mediaPlayer.time = position()
      emit(AvPlayerEvent.PositionUpdate(Millis(mediaPlayer.time), duration, false))
      allowPositionUpdate = true
    }
  }

  override fun stop() {
    if (isValid) mediaPlayer.stop()
  }

  override fun shutdown() {
    if (!isShutdown) {
      transition.setCancelled()
      seekable = false
      isShutdown = true
      if (prepared) secondStageShutdown() else shutdownOnPrepared = true
    }
  }

  override fun setEqualizer(newEq: EqPreset, applyEdits: Boolean) {
    if (newEq is VlcEqPreset) {
      if (isValid && (applyEdits || eqPreset != newEq)) {
        setPlayerPreset(newEq)
      }
    } else LOG.e { it("Preset %s is not a VlcEqPreset", newEq) }
  }

  override fun transitionTo(transition: PlayerTransition) {
    startTransition(transition, false)
  }

  private fun setPlayerPreset(preset: VlcEqPreset): Boolean {
    if (isShutdown) return false
    if (preset.isNullPreset) {
      mediaPlayer.setEqualizer(null)
    } else {
      preset.setPreset(mediaPlayer)
    }
    eqPreset = preset
    scope.launch {
//    notify new EqPreset active
    }
    return true
  }

  private inline fun getPauseTransition(immediate: () -> Boolean) =
    if (immediate()) PauseImmediateTransition() else pauseTransition

  private fun getPlayTransition(immediate: () -> Boolean) =
    if (immediate()) PlayImmediateTransition() else playTransition

  private fun secondStageShutdown() {
    removeFromQueue(this)
    if (!mediaPlayer.isReleased) {
      mediaPlayer.release()
    }
    wakeLock.release()
  }

  private fun emit(event: AvPlayerEvent) {
    scope.launch { eventFlow.emit(event) }
  }

  private fun makeEventListener() = MediaPlayer.EventListener { event ->
//    LOG._e { it("%s %s", title(), event.asString()) }
    if (!isShutdown) {
      when (event.type) {
        MediaPlayer.Event.Opening -> mediaPlayer.setMappedVolume(realVolume)
        MediaPlayer.Event.Buffering -> if (!prepared && event.bufferedEnoughForPrepare()) {
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
          shutdown()
          emit(AvPlayerEvent.PlaybackComplete)
        }
        MediaPlayer.Event.EncounteredError -> {
          prepared = false
          shutdown()
          emit(AvPlayerEvent.Error)
        }
        MediaPlayer.Event.TimeChanged -> {
          if (allowPositionUpdate) {
            emit(AvPlayerEvent.PositionUpdate(Millis(event.timeChanged), duration, true))
          }
        }
        MediaPlayer.Event.PositionChanged -> {
          event.positionChanged
        }
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
    } else {
      if (!prepared &&
        shutdownOnPrepared &&
        event.type == MediaPlayer.Event.Buffering &&
        event.bufferedEnoughForPrepare()
      ) {
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
      if (notifyPrepared) emit(AvPlayerEvent.Prepared(Millis(mediaPlayer.time), duration))
      scope.launch { newTransition.execute() }
    }
  }

  private inner class TheTransitionPlayer : TransitionPlayer {
    override val isPaused: Boolean get() = isValid && !mediaPlayer.isPlaying
    override val isPlaying: Boolean get() = isValid && mediaPlayer.isPlaying
    override val playerIsShutdown: Boolean get() = isShutdown

    override var playerVolume: Volume
      get() = volume
      set(value) {
        volume = value
      }
    override val volumeRange: VolumeRange get() = AvPlayer.DEFAULT_VOLUME_RANGE

    override val remainingTime: Millis
      get() = if (isValid) duration - mediaPlayer.time else Millis(0)

    override fun notifyPaused() = emit(AvPlayerEvent.Paused(time))

    override fun notifyPlaying() {
      emit(AvPlayerEvent.Start(firstStart))
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
